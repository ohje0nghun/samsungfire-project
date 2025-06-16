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

log.warn("📌 PR 대상 브랜치 = ${toBranch}")
log.warn("📌 PR 커밋 ID = ${commitId}")
log.warn("📌 프로젝트 = ${projectKey}, 리포지토리 = ${repoSlug}")

// ✅ dev, sit 브랜치 대상 PR만 검사
if (!(toBranch.equalsIgnoreCase("dev") || toBranch.equalsIgnoreCase("sit"))) {
    log.warn("✅ 검사 제외 브랜치: ${toBranch}")
    return RepositoryHookResult.accepted()
}

// ✅ 필수 리포트 키
List<String> requiredReports = ["report_codemind"]
List<String> foundReports = []

// 📌 커밋에 등록된 Code Insights 리포트 키 목록 조회
List<String> allReports = getAllReportKeys(projectKey, repoSlug, commitId)
log.warn("🔍 커밋에 연결된 리포트 키 목록 = ${allReports}")

requiredReports.each { String key ->
    log.warn("🟡 검사 중인 리포트 키 = ${key}")
    if (allReports.contains(key)) {
        log.warn("✅ ${key} 리포트 존재")
        foundReports << key
    } else {
        log.warn("❌ ${key} 리포트 없음")
    }
}

if (foundReports.size() < requiredReports.size()) {
    List<String> missing = requiredReports - foundReports
    log.warn("❌ 병합 거부 - 누락된 리포트 = ${missing}")
    return RepositoryHookResult.rejected(
        "Code Insights Report 누락",
        "다음 리포트가 존재하지 않습니다: ${missing.join(', ')}"
    )
}

log.warn("✅ 모든 리포트 존재 - 병합 허용")
return RepositoryHookResult.accepted()

// ==============================
// 🔍 Code Insights Report 목록 조회
// ==============================
List<String> getAllReportKeys(String projectKey, String repoSlug, String commitId) {
    try {
        String url = "http://bitbucket.techartist.xyz/rest/insights/1.0/projects/${projectKey}/repos/${repoSlug}/commits/${commitId}/reports"
        def connection = new URL(url).openConnection()
        connection.setRequestProperty("Authorization", "Bearer BBDC-OTU3NzU5NjMxMjY5OsZCwEdaVXmKivK1fra0JbDdT+1m")
        String response = connection.inputStream.text

        def matcher = response =~ /"key"\s*:\s*"([^"]+)"/
        List<String> reportKeys = []
        while (matcher.find()) {
            reportKeys << matcher.group(1)
        }

        return reportKeys
    } catch (Exception e) {
        log.warn("❗ Report 목록 조회 중 예외 발생: ${e.message}")
        return []
    }
}
















List<String> getAllReportKeys(String projectKey, String repoSlug, String commitId) {
    try {
        // 👉 SSL 인증서 검증 비활성화 설정
        def trustAllCerts = [new javax.net.ssl.X509TrustManager() {
            java.security.cert.X509Certificate[] getAcceptedIssuers() { null }
            void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
            void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
        }] as javax.net.ssl.TrustManager[]

        def sc = javax.net.ssl.SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, new java.security.SecureRandom())
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier({ hostname, session -> true } as javax.net.ssl.HostnameVerifier)

        // 👉 URL 요청
        String url = "https://bitbucket.techartist.xyz/rest/insights/1.0/projects/${projectKey}/repos/${repoSlug}/commits/${commitId}/reports"
        def connection = new URL(url).openConnection()
        connection.setRequestProperty("Authorization", "Bearer BBDC-OTU3NzU5NjMxMjY5OsZCwEdaVXmKivK1fra0JbDdT+1m")
        String response = connection.inputStream.text

        def matcher = response =~ /"key"\s*:\s*"([^"]+)"/
        List<String> reportKeys = []
        while (matcher.find()) {
            reportKeys << matcher.group(1)
        }

        return reportKeys
    }














