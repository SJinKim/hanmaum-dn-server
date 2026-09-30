<#import "template.ftl" as layout>
<#-- Confirmations such as "password updated" or a pending required action. -->
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "badge">
        <@layout.badge tone="active" glyph="check-circle"/>
    <#elseif section = "header">
        <#if messageHeader??>${kcSanitize(msg("${messageHeader}"))?no_esc}<#else>${message.summary}</#if>
    <#elseif section = "description">
        <#if messageHeader?? || requiredActions??>
            <p class="hm-desc">${message.summary}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></b></#list></#if></p>
        </#if>
    <#elseif section = "form">
        <#if !skipLink??>
            <#if pageRedirectUri?has_content>
                <a class="hm-button" href="${pageRedirectUri}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
            <#elseif actionUri?has_content>
                <a class="hm-button" href="${actionUri}">${kcSanitize(msg("proceedWithAction"))?no_esc}</a>
            <#elseif (client.baseUrl)?has_content>
                <a class="hm-button" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
            </#if>
        </#if>
    </#if>
</@layout.registrationLayout>
