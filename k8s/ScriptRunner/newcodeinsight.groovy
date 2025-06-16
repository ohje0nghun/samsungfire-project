package mergecheck

import com.atlassian.bitbucket.hook.repository.RepositoryHookResult
import com.atlassian.bitbucket.pull.PullRequestService
import com.atlassian.sal.api.component.ComponentLocator

// 🔹 PR 정보 조회
PullRequestService pullRequestService = ComponentLocator.getComponent(PullRequestService)
def pullRequest = pullRequestService.getById(
    mergeRequest.pullRequest.toRef.repository.id,
    mergeRequest.pullRequest.id
)

String toBranch = pullRequest.toRef.displayId
String commitId = pullRequest.fromRef.latestCommit
String projectKey = pullRequest.toRef.repository.project.key
String repoSlug = pullRequest.toRef.repository.slug

// ✅ dev 브랜치 대상 PR만 검사
if (!(toBranch.equalsIgnoreCase("dev") || toBranch.equalsIgnoreCase("sit"))) {
    return RepositoryHookResult.accepted()
}

// ✅ 반드시 존재해야 할 Code Insights 리포트 키 목록
List<String> requiredReports = ["codemind"]
List<String> foundReports = []

// 📌 현재 커밋에 등록된 전체 리포트 키 목록 조회
List<String> allReports = getAllReportKeys(projectKey, repoSlug, commitId)

requiredReports.each { String key ->
    if (allReports.contains(key)) {
        foundReports << key
    }
}

// ❌ 일부 리포트가 누락된 경우 병합 차단
if (foundReports.size() < requiredReports.size()) {
    List<String> missing = requiredReports - foundReports
    return RepositoryHookResult.rejected(
        "Code Insights Report 누락",
        "다음 리포트가 존재하지 않습니다: ${missing.join(', ')}"
    )
}

// ✅ 리포트가 모두 존재하면 병합 허용
return RepositoryHookResult.accepted()

// ==============================
// 🔍 Code Insights Report 목록 조회
// ==============================
List<String> getAllReportKeys(String projectKey, String repoSlug, String commitId) {
    try {
        String url = "http://bitbucket.techartist.xyz/rest/insights/1.0/projects/${projectKey}/repos/${repoSlug}/commits/${commitId}/reports"
        def connection = new URL(url).openConnection()

        // 인증 정보는 ScriptRunner 환경 변수로 추출 권장
        connection.setRequestProperty("Authorization", "Bearer BBDC-OTU3NzU5NjMxMjY5OsZCwEdaVXmKivK1fra0JbDdT+1m")

        String response = connection.inputStream.text

        // 정규식으로 "key": "xxx" 값만 추출
        def matcher = response =~ /"key"\s*:\s*"([^"]+)"/
        List<String> reportKeys = []
        while (matcher.find()) {
            reportKeys << matcher.group(1)
        }
        return reportKeys
    } catch (Exception e) {
        return []
    }
}
