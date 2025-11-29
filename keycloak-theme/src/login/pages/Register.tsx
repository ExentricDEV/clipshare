import type { JSX } from "keycloakify/tools/JSX";
import { useState, useLayoutEffect } from "react";
import type { LazyOrNot } from "keycloakify/tools/LazyOrNot";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { getKcClsx, type KcClsx } from "keycloakify/login/lib/kcClsx";
import type { UserProfileFormFieldsProps } from "keycloakify/login/UserProfileFormFieldsProps";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Box, Button, Checkbox, FormControlLabel, Link, Typography, FormHelperText } from "@mui/material";
import ClipshareLogo from "../../assets/clipshare.png";

type RegisterProps = PageProps<Extract<KcContext, { pageId: "register.ftl" }>, I18n> & {
    UserProfileFormFields: LazyOrNot<(props: UserProfileFormFieldsProps) => JSX.Element>;
    doMakeUserConfirmPassword: boolean;
};

export default function Register(props: RegisterProps) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes, UserProfileFormFields, doMakeUserConfirmPassword } = props;

    const { kcClsx } = getKcClsx({
        doUseDefaultCss,
        classes
    });

    const { messageHeader, url, messagesPerField, recaptchaRequired, recaptchaVisible, recaptchaSiteKey, recaptchaAction, termsAcceptanceRequired } =
        kcContext;

    const { msg, msgStr, advancedMsg } = i18n;

    const [isFormSubmittable, setIsFormSubmittable] = useState(false);
    const [areTermsAccepted, setAreTermsAccepted] = useState(false);

    useLayoutEffect(() => {
        (window as any)["onSubmitRecaptcha"] = () => {
            // @ts-expect-error
            document.getElementById("kc-register-form").requestSubmit();
        };

        return () => {
            delete (window as any)["onSubmitRecaptcha"];
        };
    }, []);

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            headerNode={
                <Box component="span" sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
                    <Box component="img" src={ClipshareLogo} alt="ClipShare" sx={{ height: 60 }} />
                    <Typography component="span" variant="h6" color="text.secondary" fontWeight="600">
                        {messageHeader !== undefined ? advancedMsg(messageHeader) : "Sign Up"}
                    </Typography>
                </Box>
            }
            displayMessage={messagesPerField.exists("global")}
            displayRequiredFields
        >
            <form id="kc-register-form" className={kcClsx("kcFormClass")} action={url.registrationAction} method="post">
                <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    <UserProfileFormFields
                        kcContext={kcContext}
                        i18n={i18n}
                        kcClsx={kcClsx}
                        onIsFormSubmittableValueChange={setIsFormSubmittable}
                        doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                    />

                    {termsAcceptanceRequired && (
                        <TermsAcceptance
                            i18n={i18n}
                            kcClsx={kcClsx}
                            messagesPerField={messagesPerField}
                            areTermsAccepted={areTermsAccepted}
                            onAreTermsAcceptedValueChange={setAreTermsAccepted}
                        />
                    )}

                    {recaptchaRequired && (recaptchaVisible || recaptchaAction === undefined) && (
                        <div className="form-group">
                            <div className={kcClsx("kcInputWrapperClass")}>
                                <div className="g-recaptcha" data-size="compact" data-sitekey={recaptchaSiteKey} data-action={recaptchaAction}></div>
                            </div>
                        </div>
                    )}

                    <Box sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
                        <Box sx={{ display: "flex", justifyContent: "center" }}>
                            <Link href={url.loginUrl} underline="hover" variant="body2">
                                {msg("backToLogin")}
                            </Link>
                        </Box>

                        {recaptchaRequired && !recaptchaVisible && recaptchaAction !== undefined ? (
                            <Button
                                fullWidth
                                variant="contained"
                                size="large"
                                type="submit"
                                className="g-recaptcha"
                                data-sitekey={recaptchaSiteKey}
                                data-callback="onSubmitRecaptcha"
                                data-action={recaptchaAction}
                            >
                                {msgStr("doRegister")}
                            </Button>
                        ) : (
                            <Button
                                fullWidth
                                variant="contained"
                                size="large"
                                type="submit"
                                disabled={!isFormSubmittable || (termsAcceptanceRequired && !areTermsAccepted)}
                            >
                                {msgStr("doRegister")}
                            </Button>
                        )}
                    </Box>
                </Box>
            </form>
        </Template>
    );
}

function TermsAcceptance(props: {
    i18n: I18n;
    kcClsx: KcClsx;
    messagesPerField: Pick<KcContext["messagesPerField"], "existsError" | "get">;
    areTermsAccepted: boolean;
    onAreTermsAcceptedValueChange: (areTermsAccepted: boolean) => void;
}) {
    const { i18n, messagesPerField, areTermsAccepted, onAreTermsAcceptedValueChange } = props;

    const { msg } = i18n;

    return (
        <Box sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {msg("termsTitle")}
            </Typography>
            <Box id="kc-registration-terms-text" sx={{ mb: 2, p: 2, bgcolor: "action.hover", borderRadius: 1, maxHeight: 150, overflowY: "auto" }}>
                <Typography variant="body2">{msg("termsText")}</Typography>
            </Box>

            <FormControlLabel
                control={
                    <Checkbox
                        checked={areTermsAccepted}
                        onChange={e => onAreTermsAcceptedValueChange(e.target.checked)}
                        name="termsAccepted"
                        id="termsAccepted"
                    />
                }
                label={msg("acceptTerms")}
            />

            {messagesPerField.existsError("termsAccepted") && (
                <FormHelperText error>
                    <span dangerouslySetInnerHTML={{ __html: kcSanitize(messagesPerField.get("termsAccepted")) }} />
                </FormHelperText>
            )}
        </Box>
    );
}
