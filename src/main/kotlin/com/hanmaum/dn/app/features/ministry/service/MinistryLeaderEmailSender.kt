package com.hanmaum.dn.app.features.ministry.service

/** Sends the applicant's introduction to the assigned ministry leader. */
interface MinistryLeaderEmailSender {
    fun sendApplication(
        leaderEmail: String,
        ministryName: String,
        applicantName: String,
        selfIntroduction: String,
    )
}
