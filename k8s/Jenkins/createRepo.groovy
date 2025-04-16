pipeline {
    agent any

    stages {
        stage('Create Bitbucket Repo and Branches') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'bitbucket', passwordVariable: 'BITBUCKET_PASSWORD', usernameVariable: 'BITBUCKET_USERNAME')]) {
                        def projectId = params.PROJECT_ID.split(':')[0].trim()
                        def moduleCode = params.MODULE_CODE.trim()
                        def subModuleCode = params.SUBMODULE_CODE.trim()
                        def appName = params.APPNAME.trim()
                        def selectedLandscapes = params.LANDSCAPE.tokenize(',')

                        def repos = [
                            "sfmi-${moduleCode}-components",
                            "sfmi-${moduleCode}-config",
                            "sfmi-${moduleCode}-config-app",
                            "sfmi-${moduleCode}-gateway-app",
                            "sfmi-${moduleCode}-internalgw-app",
                            "sfmi-${moduleCode}-pac"
                        ]

                        def bitbucketBaseUrl = "https://bitbucket.smart-devops.io/rest/api/1.0/projects/${projectId}/repos"

                        // ---------- 기본 구성 레포 ----------
                        repos.each { repoName ->
                            def repoExists = sh(script: """
                                curl -s -o /dev/null -w "%{http_code}" -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} \
                                ${bitbucketBaseUrl}/${repoName}
                            """, returnStdout: true).trim()

                            if (repoExists == "200") {
                                echo "Repository ${repoName} already exists. Skipping creation."
                            } else {
                                def data = """
                                    {
                                        "name": "${repoName}",
                                        "scmId": "git",
                                        "forkable": true
                                    }
                                """
                                sh """#!/bin/bash
                                    curl -X POST -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} -H "Content-Type: application/json" \
                                    -d '${data}' ${bitbucketBaseUrl}
                                """
                            }

                            def repoUrl = "https://${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD}@bitbucket.smart-devops.io/scm/${projectId}/${repoName}.git"
                            sh """#!/bin/bash
                                rm -rf .git
                                git init
                                git config user.email "ci-user@company.com"
                                git config user.name "CI/CD Pipeline"
                                git remote add origin ${repoUrl}
                            """

                            def defaultBranch = ''
                            if (repoName.endsWith("-pac") || repoName.endsWith("-config")) {
                                sh """
                                    git checkout -b dev
                                    echo "# dev branch" > README.md
                                    git add README.md
                                    git commit -m "Initial commit on dev branch"
                                    git push -u origin dev
                                """
                                defaultBranch = "dev"

                                sh """
                                    git checkout -b prd
                                    git push -u origin prd
                                """

                                if (selectedLandscapes.contains("통합")) {
                                    sh """
                                        git checkout -b stg
                                        git push -u origin stg
                                    """
                                }

                                if (selectedLandscapes.contains("검증")) {
                                    sh """
                                        git checkout -b uat
                                        git push -u origin uat
                                    """
                                }

                            } else {
                                defaultBranch = "develop"
                                sh """
                                    git checkout -b develop
                                    echo "# develop branch" > README.md
                                    git add README.md
                                    git commit -m "Initial commit on develop"
                                    git push -u origin develop
                                    git checkout -b main
                                    git push -u origin main
                                """
                            }

                            def defaultBranchApiUrl = "https://bitbucket.smart-devops.io/rest/api/1.0/projects/${projectId}/repos/${repoName}/branches/default"
                            def defaultBranchData = """{ "id": "refs/heads/${defaultBranch}" }"""
                            sh """#!/bin/bash
                                curl -X PUT -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} -H "Content-Type: application/json" \
                                -d '${defaultBranchData}' ${defaultBranchApiUrl}
                            """
                        }

                        // ---------- 추가 app/ui 레포 ----------
                        def additionalRepos = [
                            "sfmi-${moduleCode}${subModuleCode}-${appName}-app",
                            "sfmi-${moduleCode}${subModuleCode}-${appName}-ui"
                        ]

                        additionalRepos.each { repoName ->
                            def repoExists = sh(script: """
                                curl -s -o /dev/null -w "%{http_code}" -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} \
                                ${bitbucketBaseUrl}/${repoName}
                            """, returnStdout: true).trim()

                            if (repoExists == "200") {
                                echo "Repository ${repoName} already exists. Skipping creation."
                            } else {
                                def data = """
                                    {
                                        "name": "${repoName}",
                                        "scmId": "git",
                                        "forkable": true
                                    }
                                """
                                sh """#!/bin/bash
                                    curl -X POST -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} -H "Content-Type: application/json" \
                                    -d '${data}' ${bitbucketBaseUrl}
                                """
                            }

                            def repoUrl = "https://${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD}@bitbucket.smart-devops.io/scm/${projectId}/${repoName}.git"
                            sh """#!/bin/bash
                                rm -rf .git
                                git init
                                git config user.email "ci-user@company.com"
                                git config user.name "CI/CD Pipeline"
                                git remote add origin ${repoUrl}
                                git checkout -b predevelop
                                echo "# predevelop branch" > README.md
                                git add README.md
                                git commit -m "Initial commit on predevelop"
                                git push -u origin predevelop
                                git checkout -b develop
                                git push -u origin develop
                                git checkout -b main
                                git push -u origin main
                            """

                            def defaultBranchApiUrl = "https://bitbucket.smart-devops.io/rest/api/1.0/projects/${projectId}/repos/${repoName}/branches/default"
                            def defaultBranchData = """{ "id": "refs/heads/develop" }"""
                            sh """#!/bin/bash
                                curl -X PUT -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} -H "Content-Type: application/json" \
                                -d '${defaultBranchData}' ${defaultBranchApiUrl}
                            """
                        }
                    }
                }
            }
        }
    }
}
