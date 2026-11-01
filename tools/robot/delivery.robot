*** Settings ***
Library    ${CURDIR}/RelayForgeLibrary.py
Library    Collections
Suite Setup    Start Local Stack
Test Setup    Clear Receiver Requests

*** Variables ***
@{EXPECTED_TWO_DELIVERED}    DELIVERED    DELIVERED
@{EXPECTED_ONE_DELIVERED}    DELIVERED

*** Test Cases ***
Accepted Delivery Reaches Receiver Once
    ${event_type}=    Create Unique Event Type    robot.accept
    ${endpoint}=    Configure Endpoint For Event    ${event_type}    200
    ${event}=    Create Event    ${event_type}
    ${result}=    Wait For Delivery    ${event}[id]    ${endpoint}[id]
    Should Be Equal    ${result}[delivery_status]    DELIVERED
    Should Be Equal    ${result}[event_status]    DONE
    Should Be Equal As Integers    ${result}[attempt_count]    1
    Should Be Equal As Integers    ${result}[captured_request_count]    1

HTTP 503 Is Retried Until Attempts Are Exhausted
    ${event_type}=    Create Unique Event Type    robot.retry
    ${endpoint}=    Configure Endpoint For Event    ${event_type}    503
    ${event}=    Create Event    ${event_type}
    ${result}=    Wait For Delivery    ${event}[id]    ${endpoint}[id]
    Should Be Equal    ${result}[delivery_status]    FAILED
    Should Be Equal    ${result}[event_status]    DONE
    Should Be True    ${result}[attempt_count] >= 2
    Should Be Equal As Integers    ${result}[captured_request_count]    ${result}[attempt_count]

Non Retryable HTTP 400 Is Attempted Once
    ${event_type}=    Create Unique Event Type    robot.bad-request
    ${endpoint}=    Configure Endpoint For Event    ${event_type}    400
    ${event}=    Create Event    ${event_type}
    ${result}=    Wait For Delivery    ${event}[id]    ${endpoint}[id]
    Should Be Equal    ${result}[delivery_status]    FAILED
    Should Be Equal    ${result}[event_status]    DONE
    Should Be Equal As Integers    ${result}[attempt_count]    1
    Should Be Equal As Integers    ${result}[captured_request_count]    1

Event Creates A Delivery For Each Matching Endpoint
    ${event_type}=    Create Unique Event Type    robot.multicast
    ${first_endpoint}=    Configure Endpoint For Event    ${event_type}    200
    ${second_endpoint}=    Configure Endpoint For Event    ${event_type}    201
    ${event}=    Create Event    ${event_type}
    ${result}=    Wait For Event Deliveries    ${event}[id]
    Should Be Equal    ${result}[event_status]    DONE
    Should Be Equal As Integers    ${result}[delivery_count]    2
    Lists Should Be Equal    ${result}[delivery_statuses]    ${EXPECTED_TWO_DELIVERED}
    Should Not Be Equal    ${first_endpoint}[id]    ${second_endpoint}[id]

Inactive Endpoint Does Not Receive A Delivery
    ${event_type}=    Create Unique Event Type    robot.inactive
    ${endpoint}=    Configure Endpoint For Event    ${event_type}    200
    Set Endpoint Active    ${endpoint}[id]    ${False}
    ${event}=    Create Event    ${event_type}
    ${result}=    Wait For Event Deliveries    ${event}[id]
    Should Be Equal    ${result}[event_status]    DONE
    Should Be Equal As Integers    ${result}[delivery_count]    0

Unmatched Event Completes With No Deliveries
    ${event_type}=    Create Unique Event Type    robot.unmatched
    ${event}=    Create Event    ${event_type}
    ${result}=    Wait For Event Deliveries    ${event}[id]
    Should Be Equal    ${result}[event_status]    DONE
    Should Be Equal As Integers    ${result}[delivery_count]    0

Idempotency Key Does Not Create Duplicate Events Or Deliveries
    ${event_type}=    Create Unique Event Type    robot.idempotent
    ${endpoint}=    Configure Endpoint For Event    ${event_type}    200
    ${key}=    Create Unique Event Type    robot-key
    ${first_event}=    Create Event    ${event_type}    ${key}
    ${second_event}=    Create Event    ${event_type}    ${key}
    Should Be Equal    ${first_event}[id]    ${second_event}[id]
    ${result}=    Wait For Event Deliveries    ${first_event}[id]
    Should Be Equal As Integers    ${result}[delivery_count]    1
    Should Be Equal    ${result}[delivery_statuses]    ${EXPECTED_ONE_DELIVERED}
    Should Be Equal    ${endpoint}[id]    ${result}[deliveries][0][endpointId]

Subscription Defaults To A Sixty Second Retry Interval
    ${event_type}=    Create Unique Event Type    robot.default-retry
    ${url}=    Set Receiver Response    202
    ${endpoint}=    Register Endpoint Url    ${url}    Robot default retry interval
    ${subscription}=    Create Subscription With Default Retry    ${endpoint}[id]    ${event_type}
    Should Be Equal As Integers    ${subscription}[retryIntervalSeconds]    60

Unknown Test Receiver Route Is Rejected
    ${accept_url}=    Set Receiver Response    200
    ${invalid_url}=    Catenate    SEPARATOR=    ${accept_url}    /admin
    ${status}=    Endpoint Registration Http Status    ${invalid_url}
    Should Be Equal As Integers    ${status}    400

Looking Up Deliveries For Unknown Event Returns 404
    ${missing_id}=    Create Missing Event Id
    ${status}=    Event Deliveries Http Status    ${missing_id}
    Should Be Equal As Integers    ${status}    404
