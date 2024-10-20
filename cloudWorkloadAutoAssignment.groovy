import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.util.Base64

/*
 * This script automates the process of assigning newly created issues in Jira. In this example, I use a Jira Software
 * project for bug tracking, in which the workload will assign issues to the developer, in a specific technology group,
 *  with the least workload issues. The script listens for issue creation events, and determines the appropriate group based 
 * on a custom field value (Java or Python, and assigns the issue to the developer with the fewest unresolved issues in 
 * the BTT project.
 */

// Define constants for Jira API access
def BASE_URI = 'https://your-jira-instance.atlassian.net' // Replace with your Jira instance URL
def EMAIL = 'your-email@example.com' // Replace with your email
def API_TOKEN = 'your-api-token' // Replace with your API token
def CUSTOM_FIELD_ID = "customfield_10393" // ID of the custom field for technology expertise
def GROUPS = [
    'Java'  : 'java-group-id',    // Replace with actual group ID for Java developers
    'Python': 'python-group-id'   // Replace with actual group ID for Python developers
]

// Construct the Basic Authentication Header
def authString = "${EMAIL}:${API_TOKEN}"
def authHeader = "Basic " + Base64.getEncoder().encodeToString(authString.getBytes("UTF-8"))

println("Base URI: ${BASE_URI}")

// Main script execution starts here
if (event?.issue) {
    // Retrieve the issue key from the event
    def issueKey = event.issue.key
    // Process the issue for automatic assignment
    processIssue(issueKey, BASE_URI, authHeader, CUSTOM_FIELD_ID, GROUPS)
} else {
    println("Event data is missing or incomplete.")
}

// Function to process the issue and assign it to the appropriate developer
def processIssue(issueKey, baseUri, authHeader, customFieldId, groups) {
    // Fetch issue details from Jira
    def issueDetails = getIssueDetails(issueKey, baseUri, authHeader)

    if (issueDetails) {
        // Get the expertise label from the custom field
        def expertiseLabel = issueDetails.fields[customFieldId]?.value

        if (!expertiseLabel) {
            println("Bug Technology field is not set or has an unexpected format for issue ${issueKey}")
            return
        }

        // Determine the developer group based on expertise
        def groupId = groups[expertiseLabel]
        if (!groupId) {
            println("No group found for expertise ${expertiseLabel}")
            return
        }

        println("Processing issue ${issueKey} for group with ID ${groupId}")

        // Get users in the determined group
        def usersInGroup = getUsersInGroup(groupId, baseUri, authHeader)
        def minWorkload = Integer.MAX_VALUE
        def selectedUser = null

        // Calculate workload for each user and find the one with the least workload
        usersInGroup.each { user ->
            def workload = calculateWorkload(user, baseUri, authHeader)
            println("User ${user.displayName} has workload ${workload}")
            if (workload < minWorkload) {
                minWorkload = workload
                selectedUser = user
            }
        }

        // Assign the issue to the selected user
        if (selectedUser) {
            assignIssue(issueKey, selectedUser.accountId, baseUri, authHeader)
        } else {
            println("No suitable developer found for issue ${issueKey}")
        }
    } else {
        println("Could not retrieve issue details for ${issueKey}.")
    }
}

// Function to get users in a specific group
def getUsersInGroup(groupId, baseUri, authHeader) {
    def users = []
    def startAt = 0
    def maxResults = 50
    def isLast = false

    while (!isLast) {
        try {
            // Construct the URL to get users in the group
            def groupUri = "${baseUri}/rest/api/3/group/member?groupId=${URLEncoder.encode(groupId, 'UTF-8')}&startAt=${startAt}&maxResults=${maxResults}"
            def connection = new URL(groupUri).openConnection() as HttpURLConnection
            connection.setRequestProperty('Authorization', authHeader)
            connection.setRequestProperty('Accept', 'application/json')
            connection.setRequestMethod('GET')

            // Check the response and parse the list of users
            if (connection.responseCode == 200) {
                def response = connection.inputStream.text
                def jsonResponse = new JsonSlurper().parseText(response)
                users.addAll(jsonResponse.values)
                isLast = jsonResponse.isLast
                startAt += maxResults
            } else {
                println("Failed to fetch users in group. Response code: ${connection.responseCode}")
                break
            }
        } catch (Exception e) {
            println("Exception in getUsersInGroup: ${e.message}")
            break
        }
    }
    return users
}

// Function to calculate the workload of a user based on unresolved issues in the BTT project
def calculateWorkload(user, baseUri, authHeader) {
    try {
        // Construct JQL to fetch unresolved issues assigned to the user in the BTT project
        def jql = "project = BTT AND assignee = ${user.accountId} AND statusCategory != Done"
        def searchUri = "${baseUri}/rest/api/3/search?jql=${URLEncoder.encode(jql, 'UTF-8')}"
        def connection = new URL(searchUri).openConnection() as HttpURLConnection
        connection.setRequestProperty('Authorization', authHeader)
        connection.setRequestProperty('Accept', 'application/json')
        connection.setRequestMethod('GET')

        // Parse the response to get the total number of unresolved issues
        if (connection.responseCode == 200) {
            def response = connection.inputStream.text
            def jsonResponse = new JsonSlurper().parseText(response)
            return jsonResponse.total
        } else {
            println("Failed to calculate workload. Response code: ${connection.responseCode}")
            return Integer.MAX_VALUE
        }
    } catch (Exception e) {
        println("Exception in calculateWorkload: ${e.message}")
        return Integer.MAX_VALUE
    }
}

// Function to assign the issue to a user
def assignIssue(issueKey, accountId, baseUri, authHeader) {
    try {
        // Construct the URL to assign the issue
        def assignUri = "${baseUri}/rest/api/3/issue/${issueKey}/assignee"
        def connection = new URL(assignUri).openConnection() as HttpURLConnection
        connection.setRequestProperty('Authorization', authHeader)
        connection.setRequestMethod('PUT')
        connection.doOutput = true
        connection.setRequestProperty('Content-Type', 'application/json')
        def requestBody = '{"accountId": "' + accountId + '"}'
        connection.outputStream.write(requestBody.getBytes('UTF-8'))

        // Check if the assignment was successful
        if (connection.responseCode == 204) {
            println("Issue ${issueKey} assigned to user with accountId: ${accountId}")
        } else {
            println("Failed to assign issue. Response code: ${connection.responseCode}")
            println("Response message: " + connection.errorStream.text)
        }
    } catch (Exception e) {
        println("Exception in assignIssue: ${e.message}")
    }
}

// Function to get issue details from Jira
def getIssueDetails(issueKey, baseUri, authHeader) {
    try {
        // Construct the URL to fetch issue details
        def issueUri = "${baseUri}/rest/api/3/issue/${URLEncoder.encode(issueKey, 'UTF-8')}"
        println("Fetching issue details from: ${issueUri}")

        def connection = new URL(issueUri).openConnection() as HttpURLConnection
        connection.setRequestProperty('Authorization', authHeader)
        connection.setRequestProperty('Accept', 'application/json')
        connection.setRequestMethod('GET')

        // Parse the issue details from the response
        if (connection.responseCode == 200) {
            def response = connection.inputStream.text
            def jsonResponse = new JsonSlurper().parseText(response)
            return jsonResponse
        } else {
            println("Failed to fetch issue details. Response code: ${connection.responseCode}")
            println("Response message: " + connection.errorStream.text)
            return null
        }
    } catch (Exception e) {
        println("Exception in getIssueDetails: ${e.message}")
        return null
    }
}
