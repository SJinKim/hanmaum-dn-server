<#import "template.ftl" as layout>
<#-- Expired links (reset, verify, stale login) get the Figma "링크 만료" screen; everything else the plain error. -->
<#assign expired = message?has_content && [
    msg("expiredActionTokenNoSessionMessage"), msg("expiredActionMessage"),
    msg("expiredActionTokenSessionExistsMessage"), msg("expiredCodeMessage")
]?seq_contains(message.summary)>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "badge">
        <#if expired><@layout.badge tone="pending" glyph="exclamation-triangle"/><#else><@layout.badge tone="rejected" glyph="exclamation-triangle"/></#if>
    <#elseif section = "header">
        <#if expired>${msg("hmExpiredTitle")}<#else>${kcSanitize(msg("errorTitle"))?no_esc}</#if>
    <#elseif section = "description">
        <p class="hm-desc"><#if expired>${msg("hmExpiredDescription")}<#else>${kcSanitize(message.summary)?no_esc}</#if></p>
    <#elseif section = "form">
        <#if !skipLink??>
            <#if expired && client?? && client.baseUrl?has_content>
                <a class="hm-button" id="backToApplication" href="${client.baseUrl}">${msg("hmGoToLogin")}</a>
            <#elseif client?? && client.baseUrl?has_content>
                <a class="hm-button" id="backToApplication" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
            </#if>
        </#if>
    </#if>
</@layout.registrationLayout>
