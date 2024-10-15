import groovy.json.JsonOutput

// Define the project key this script will operate on
def projectKey = 'AFJ'

// Check if the issue is within the specified project
if (issue.fields.project.key != projectKey) {
    // If not, exit the script
    return
}

// Get the display name of the issue creator
def author = issue.fields.creator.displayName

// Add a comment to the issue using Jira's REST API
def commentResp = post("/rest/api/2/issue/${issue.key}/comment")
    .header('Content-Type', 'application/json') // Set the content type to JSON
    .body([
        body: """Thank you ${author} for creating a support request.

We'll respond to your query within 24 hours.

In the meantime, please read our documentation: http://example.com/documentation"""
    ])
    .asObject(Map)

// Ensure the comment was added successfully, expecting a 201 status
assert commentResp.status == 201

// Define the form template payload, replace with your actual form template ID
def formTemplateId = "your-form-template-id"
def formPayload = [
    formTemplate: [
        id: formTemplateId
    ]
]

// Construct the URL for the Forms API, replace with your specific instance URL
def formApiUrl = "https://api.atlassian.com/jira/forms/cloud/your-instance-id/issue/${issue.key}/form"

// Add the form to the issue using the Forms API
def formResp = post(formApiUrl)
    .header('Content-Type', 'application/json') // Set the content type to JSON
    .header('Accept', 'application/json') // Set the accept header to JSON
    .basicAuth("your-email@example.com", "your-api-token") // Replace with your email and API token
    .body(JsonOutput.toJson(formPayload)) // Convert the payload to JSON
    .asObject(Map)

// Check if the form was added successfully, expecting a 200 status
assert formResp.status == 200

println "Form added successfully to issue ${issue.key}"
