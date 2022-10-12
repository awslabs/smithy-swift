//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import Foundation
import XCTest
@testable import ClientRuntime

class WaiterSchedulerTests: XCTestCase {

    func test_immediateRequestToBeMadeOnCreation() async throws {
        let subject = WaiterScheduler(minDelay: 2.0, maxDelay: 120.0, maximumWaitTime: 360.0)
        XCTAssertEqual(subject.currentDelay, 0.0)
        XCTAssertFalse(subject.isExpired)
    }

    func test_updateAfterRetry_updatesAttemptAfterCall() async throws {
        let subject = WaiterScheduler(minDelay: 2.0, maxDelay: 120.0, maximumWaitTime: 360.0)
        let base = Date()
        subject.now = { base }
        XCTAssertEqual(subject.attempt, 0)
        _ = subject.updateAfterRetry()
        XCTAssertEqual(subject.attempt, 1)
        _ = subject.updateAfterRetry()
        XCTAssertEqual(subject.attempt, 2)
    }

    func test_updateAfterRetry_setsExpiredWhenCalledAfterMaxTime() async throws {
        let subject = WaiterScheduler(minDelay: 2.0, maxDelay: 120.0, maximumWaitTime: 360.0)
        let base = Date()
        subject.now = { base }
        _ = subject.updateAfterRetry()
        subject.now = { base + 361.0 }
        XCTAssertFalse(subject.isExpired)
        _ = subject.updateAfterRetry()
        XCTAssertTrue(subject.isExpired)
    }

    func test_updateAfterRetry_proceedsToExpirationAndStops() async throws {
        // Perform this test 1000 times with random time settings
        // This helps to account for randomness in the subject under test
        (1...1000).forEach { _ in
            // Generate random settings for the scheduler
            let minDelay = TimeInterval.random(in: 5.0...10.0)
            let maxDelay = TimeInterval.random(in: 10.0...20.0)
            let maximumWaitTime = TimeInterval.random(in: 20.0...240.0)

            // Create a test subject, and mock its current time
            let subject = WaiterScheduler(minDelay: minDelay, maxDelay: maxDelay, maximumWaitTime: maximumWaitTime)

            // Simulate the passage of time by replacing the now() closure on subject
            let base = Date()
            var elapsed: TimeInterval = 0.0
            subject.now = { base + elapsed }

            var iteration = 0

            // Simulate the making of multiple requests at the prescribed times, until timeout
            while !subject.isExpired {
                iteration += 1

                // Update the scheduler to indicate time of the next retry
                let retryInfo = subject.updateAfterRetry()

                // Verify that retryInfo is correct
                XCTAssertEqual(retryInfo.attempt, iteration)
                XCTAssertEqual(retryInfo.timeUntilNextAttempt, subject.currentDelay, accuracy: 0.0001)
                XCTAssertEqual(retryInfo.timeUntilTimeout, subject.maximumWaitTime - elapsed, accuracy: 0.0001)

                // Read the currentDelay from the subject
                let delay = subject.currentDelay
                // Add in a little extra random time to account for time to complete a request
                let requestInflightTime = TimeInterval.random(in: 0.0...2.0)
                // Add these two times to the total elapsed time as if it's now time for the next request
                elapsed += delay + requestInflightTime

                // Ensure a request never takes place after the max wait time expires
                XCTAssert(elapsed < subject.maximumWaitTime)
            }
        }
    }
}
