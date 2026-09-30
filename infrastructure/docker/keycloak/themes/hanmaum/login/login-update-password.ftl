<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("updatePasswordTitle")}
    <#elseif section = "description">
        <p class="hm-desc">${msg("hmUpdatePasswordDescription")}</p>
    <#elseif section = "form">
        <form id="kc-passwd-update-form" class="hm-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <div class="hm-fields">
                <div class="hm-field">
                    <label for="password-new" class="hm-label">${msg("passwordNew")}</label>
                    <input type="password" id="password-new" name="password-new" class="hm-input" autofocus
                           autocomplete="new-password" dir="ltr" aria-describedby="password-hint"
                           <#if messagesPerField.existsError('password')>aria-invalid="true"</#if>/>
                    <span id="password-hint" class="hm-hint">${msg("hmPasswordHint")}</span>
                </div>
                <div class="hm-field">
                    <label for="password-confirm" class="hm-label">${msg("passwordConfirm")}</label>
                    <input type="password" id="password-confirm" name="password-confirm" class="hm-input"
                           autocomplete="new-password" dir="ltr"
                           <#if messagesPerField.existsError('password-confirm')>aria-invalid="true"</#if>/>
                </div>
            </div>
            <#-- Same field as the base theme: after a reset every other session ends. -->
            <input type="hidden" name="logout-sessions" value="on"/>
            <button class="hm-button" name="login" type="submit">${msg("hmChangePassword")}</button>
            <#if isAppInitiatedAction??>
                <button class="hm-button hm-button--secondary" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
            </#if>
        </form>
    </#if>
</@layout.registrationLayout>
