import com.atlassian.bitbucket.pull.PullRequest
import com.atlassian.bitbucket.repository.Repository
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult
import groovy.json.JsonSlurper
import java.net.HttpURLConnection

def token = "BBDC-NjQ5ODkyMjAyMDkwOlEqPXpK2scgZYyySwo2L4OOUI3+"
def baseUrl = "https://bitbucket.smart-devops.io"

// ScriptRunner context: mergeRequest
PullRequest pr = mergeRequest.pullRequest
Repository repo = pr.toRef.repository
def projectKey = repo.project.key
def repoSlug = repo.slug
def prId = pr.id

// API 호출
def url = "${baseUrl}/rest/api/1.0/projects/${projectKey}/repos/${repoSlug}/pull-requests/${prId}/changes"
def connection = (HttpURLConnection) new URL(url).openConnection()
connection.setRequestMethod("GET")
connection.setRequestProperty("Authorization", "Bearer ${token}")
connection.setRequestProperty("Content-Type", "application/json")

def responseCode = connection.getResponseCode()
if (responseCode != 200) {
    return RepositoryHookResult.rejected("REST 오류", "변경 파일을 가져오는 데 실패했습니다: HTTP ${responseCode}")
}

def json = new JsonSlurper().parseText(connection.inputStream.text) as Map
def files = json["values"] ?: []

def workflowChanged = files.any { Map file ->
    file.toString()?.contains("workflow.yaml")
}

if (workflowChanged) {
    return RepositoryHookResult.rejected(
        "🚫 병합 차단됨",
        "'workflow.yaml' 파일이 변경되어 PR을 병합할 수 없습니다."
    )
}

return RepositoryHookResult.accepted()
