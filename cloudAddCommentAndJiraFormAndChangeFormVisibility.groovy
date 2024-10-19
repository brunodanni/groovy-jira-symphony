import groovy.json.JsonOutput

/*
 * This script is designed to automate the process of adding a form to a Jira issue
 * and making it externally visible. It performs the following steps:
 * 1. Checks if the issue belongs to a specific project.
 * 2. Posts a comment on the issue (asking the customer to complete the form added to the issue - e.g. "Attend Conference").
 * 3. Adds a form to the issue using the Jira Forms API.
 * 4. Retrieves the form ID from the list of forms attached to the issue.
 * 5. Updates the form visibility to external using the form's ID.
 */

// Define the project key to restrict script execution to a specific project
def projectKey = 'AFJ'

// Check if the issue belongs to the specified project
if (issue.fields.project.key != projectKey) {
    return // Exit if the issue is not in the specified project
}

// Retrieve the display name of the issue's creator
def author = issue.fields.creator.displayName

// Add a comment to the issue to acknowledge receipt of the support request
def commentResp = post("/rest/api/2/issue/${issue.key}/comment")
    .header('Content-Type', 'application/json')
    .body([
            body: """Thank you ${author}, for submitting a support request regarding the Attend Conference.

We will respond to your inquiry within 24 hours.

Meanwhile, please take a moment to review and complete the Attend Conference Questionnaire that has been added to your issue at [${issue.key}|https://your-jira-instance/servicedesk/customer/portal/17/${issue.key}].

Best regards!"""
    ])
    .asObject(Map)

// Ensure the comment was successfully added
assert commentResp.status == 201

// Define the form template ID
def formTemplateId = "your-form-template-id" // Replace with your actual form template ID

// Create the payload for adding the form
def formPayload = [
    formTemplate: [
        id: formTemplateId
    ]
]

// Construct the API URL for adding a form to the issue
def formApiUrl = "https://api.atlassian.com/jira/forms/cloud/your-cloud-id/issue/${issue.key}/form"

// Add the form to the issue
def formResp = post(formApiUrl)
    .header('Content-Type', 'application/json')
    .header('Accept', 'application/json')
    .basicAuth("your-email@example.com", "your-api-token") // Replace with your email and API token
    .body(JsonOutput.toJson(formPayload))
    .asObject(Map)

// Verify that the form was added successfully
assert formResp.status == 200
println "POST Form Response: ${formResp.body}"

// Introduce a delay to ensure the form is processed
sleep(2000) // Sleep for 2 seconds

// Construct the API URL to retrieve forms attached to the issue
def getFormApiUrl = "https://api.atlassian.com/jira/forms/cloud/your-cloud-id/issue/${issue.key}/form"

// Retrieve the list of forms attached to the issue
def getFormResp = get(getFormApiUrl)
    .header('Accept', 'application/json')
    .basicAuth("your-email@example.com", "your-api-token") // Replace with your email and API token
    .asObject(List) // Expecting a List response

println "GET Form API Response Status: ${getFormResp.status}"
println "GET Form API Response: ${getFormResp.body}"

// Ensure the response is valid before extracting the formId
assert getFormResp.status == 200 && getFormResp.body != null

// Extract the formId from the response
def formId = getFormResp.body.find { it.formTemplate.id == formTemplateId }?.id

// Check if formId was successfully retrieved
if (!formId) {
    println "Failed to retrieve formId from the response."
    return
}

println "Form added successfully to issue ${issue.key} with formId ${formId}"

// Construct the API URL to change the form visibility to external
def externalApiUrl = "https://api.atlassian.com/jira/forms/cloud/your-cloud-id/issue/${issue.key}/form/${formId}/action/external"

// Update the form visibility to external
def externalResp = put(externalApiUrl)
    .header('Content-Type', 'application/json')
    .header('Accept', 'application/json')
    .basicAuth("your-email@example.com", "your-api-token") // Replace with your email and API token
    .asObject(Map)

// Check if the PUT request was successful
assert externalResp.status == 200

println "Form visibility changed to external for formId ${formId} on issue ${issue.key}"
