<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "badge">
        <@layout.badge tone="active" glyph="check-circle"/>
    <#elseif section = "header">
        ${msg("emailVerifyTitle")}
    <#elseif section = "description">
        <p class="hm-desc">${msg("emailVerifyInstruction1", (verifyEmail!user.email))}</p>
    <#elseif section = "form">
        <#if isAppInitiatedAction??>
            <form id="kc-verify-email-form" class="hm-form" action="${url.loginAction}" method="post">
                <button class="hm-button" type="submit">${msg("emailVerifyResend")}</button>
                <button class="hm-button hm-button--secondary" type="submit" name="cancel-aia" value="true" formnovalidate>${msg("doCancel")}</button>
            </form>
        <#else>
            <p class="hm-note">${msg("hmVerifyNotReceived")} <a href="${url.loginAction}">${msg("hmVerifyResend")}</a></p>
            <@layout.backRow href=url.loginRestartFlowUrl/>
        </#if>
    </#if>
</@layout.registrationLayout>
