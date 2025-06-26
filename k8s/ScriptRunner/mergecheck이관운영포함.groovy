package cic

// Bitbucket에서 PR 병합을 제어하기 위한 스크립트

import com.atlassian.bitbucket.hook.repository.RepositoryHookResult
import com.atlassian.bitbucket.pull.PullRequestService
import com.atlassian.sal.api.component.ComponentLocator
import groovy.json.JsonSlurper
import cic.repository.RepositoryResolver

// PullRequestService를 이용해 PR 정보를 가져오기 위해 컴포넌트 로딩
def pullRequestService = ComponentLocator.getComponent(PullRequestService)

// 현재 PR 정보를 가져옴
def pullRequest = pullRequestService.getById(
    mergeRequest.pullRequest.toRef.repository.id,
    mergeRequest.pullRequest.id
)

// PR 관련 정보 추출
def fromBranch = pullRequest.fromRef.displayId
def toBranch = pullRequest.toRef.displayId
def commitId = pullRequest.fromRef.latestCommit
def projectKey = pullRequest.toRef.repository.project.key
def repoSlug = pullRequest.toRef.repository.slug

log.warn("📌 PR 대상 브랜치 = ${toBranch}")
log.warn("📌 PR 커밋 ID = ${commitId}")
log.warn("📌 프로젝트 = ${projectKey}, 리포지토리 = ${repoSlug}")

// GitOps 관리 리포지토리는 검사 대상에서 제외
if (RepositoryResolver.isGitOpsRepo(repoSlug)) {
    log.warn("⚙️ 제외 리포지토리로 검사 생략됨")
    return RepositoryHookResult.accepted()
}

// 대상 브랜치가 sit가 아닐 경우에도 검사 생략
if (!toBranch.equalsIgnoreCase("sit")) {
    log.warn("⚙️ 대상 브랜치가 sit가 아니므로 검사 생략")
    return RepositoryHookResult.accepted()
}

// 필수 통과 조건인 Code Insight Report 목록
def requiredReports = ["report_codemind", "report_fortify", "report_changeminer"]

// 선택 리포트: 존재 시 반드시 PASS여야 병합 가능
def optionalReports = ["report_projectbasepoint", "report_securityreview", "report_unittest"]

// 실패한 리포트를 저장할 리스트
def failedRequiredReports = []
def failedOptionalReports = []

// Code Insights API 호출하여 리포트 결과 가져오기
def reportStatusMap = getAllInsightReports(projectKey, repoSlug, commitId)
log.warn("🔍 조회된 리포트 키 = ${reportStatusMap.keySet().toList()}")

// 필수 리포트 검사: 존재하지 않거나 PASS가 아니면 실패 처리
requiredReports.each { reportKey ->
    def status = reportStatusMap[reportKey]
    if (!"PASS".equalsIgnoreCase(status)) {
        failedRequiredReports << "${reportKey} (${status ?: 'N/A'})"
    }
}

// 선택 리포트 검사: 존재 시 PASS가 아니면 실패, 존재하지 않으면 무시
optionalReports.each { reportKey ->
    def status = reportStatusMap[reportKey]
    def reportName = reportKey.replace("report_", "")  // 로그 출력을 위한 이름 정리

    if (status == null) {
        log.warn("🟡 ${reportName} 리포트가 존재하지 않습니다 (선택 항목이므로 무시)")
    } else if (!"PASS".equalsIgnoreCase(status)) {
        log.warn("❌ ${reportName} 선택 리포트 FAIL 상태 → 병합 거부 대상")
        failedOptionalReports << "${reportName} (${status})"
    } else {
        log.warn("✅ ${reportName} 선택 리포트 PASS")
    }
}

// 하나라도 실패한 리포트가 있다면 병합 거부
if (!failedRequiredReports.isEmpty() || !failedOptionalReports.isEmpty()) {
    log.warn("❌ 병합 거부 - 실패한 필수 리포트 = ${failedRequiredReports}")
    log.warn("❌ 병합 거부 - 실패한 선택 리포트 = ${failedOptionalReports}")

    def allFailedReports = failedRequiredReports + failedOptionalReports
    def message = ""

    // 실패한 리포트가 1개면 줄바꿈 없이 출력, 2개 이상이면 한 줄로 , 구분
    if (allFailedReports.size() == 1) {
        message = "다음 리포트가 PASS 상태가 아닙니다: ${allFailedReports[0]}"
    } else {
        message = "다음 리포트가 PASS 상태가 아닙니다: ${allFailedReports.join(', ')}"
    }

    return RepositoryHookResult.rejected("Merge Check Failed", message)
}

// 모든 조건을 통과한 경우 병합 허용
return RepositoryHookResult.accepted()


// ========== Code Insight API 호출 함수 ========== //
Map<String, String> getAllInsightReports(String projectKey, String repoSlug, String commitId) {
    def result = [:]
    try {
        // REST API 호출 URL
        def url = "https://bitbucket.techartist.xyz/rest/insights/1.0/projects/${projectKey}/repos/${repoSlug}/commits/${commitId}/reports"

        // API 연결 및 인증 헤더 설정
        def conn = new URL(url).openConnection()
        conn.setRequestProperty("Authorization", "Basic " + "devadmin:tkatjdsfmi1!".bytes.encodeBase64().toString())

        // JSON 파싱
        def parsed = new JsonSlurper().parse(conn.inputStream)

        // 결과가 리스트 형식으로 올 경우 report key/result 추출
        if (parsed instanceof Map && parsed.values instanceof List) {
            parsed.values.each { report ->
                if (report instanceof Map && report.key && report.result) {
                    result[report.key] = report.result
                }
            }
        } else {
            log.warn("❗ 예상과 다른 Insights 응답 형식: ${parsed.getClass().name}, keys = ${parsed.keySet()}")
        }
    } catch (Exception e) {
        log.warn("❗ Insights API 호출 실패: ${e.class.name} - ${e.message}")
    }

    return result
}
