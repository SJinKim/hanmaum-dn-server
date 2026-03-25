package com.hanmaum.dn.app.features.members.api.v2

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/members") // Gleicher Pfad wie v1!
class MemberControllerV2 {
    @GetMapping
    fun getAllMembersV2(): String = "Hier ist die ZUKUNFT (Version 2) - Neues JSON Format!"
}
