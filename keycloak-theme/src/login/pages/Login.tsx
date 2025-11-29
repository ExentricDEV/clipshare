import { useState } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Box, Button, Checkbox, Divider, FormControlLabel, IconButton, InputAdornment, Link, TextField, Typography } from "@mui/material";
import { Eye, EyeOff, Lock, User } from "lucide-react";
import ClipshareLogo from "../../assets/clipshare.png";

export default function Login(props: PageProps<Extract<KcContext, { pageId: "login.ftl" }>, I18n>) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

    const { social, realm, url, usernameHidden, login, auth, registrationDisabled, messagesPerField } = kcContext;

    const { msg, msgStr } = i18n;

    const [isLoginButtonDisabled, setIsLoginButtonDisabled] = useState(false);
    const [isPasswordRevealed, setIsPasswordRevealed] = useState(false);

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            displayMessage={!messagesPerField.existsError("username", "password")}
            headerNode={
                <Box component="span" sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
                    <Box component="img" src={ClipshareLogo} alt="ClipShare" sx={{ height: 60 }} />
                    <Typography component="span" variant="h6" color="text.secondary" fontWeight="600">
                        {"Sign In"}
                    </Typography>
                </Box>
            }
            displayInfo={realm.password && realm.registrationAllowed && !registrationDisabled}
            infoNode={
                <Box sx={{ textAlign: "center", mt: 2 }}>
                    <Typography variant="body2" color="text.secondary">
                        {msg("noAccount")}{" "}
                        <Link href={url.registrationUrl} underline="hover" fontWeight="medium">
                            {msg("doRegister")}
                        </Link>
                    </Typography>
                </Box>
            }
            socialProvidersNode={
                <>
                    {realm.password && social?.providers !== undefined && social.providers.length !== 0 && (
                        <Box sx={{ mt: 3 }}>
                            <Divider sx={{ mb: 3 }}>
                                <Typography variant="body2" color="text.secondary">
                                    {msg("identity-provider-login-label")}
                                </Typography>
                            </Divider>
                            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                                {social.providers.map(p => (
                                    <Button
                                        key={p.alias}
                                        variant="outlined"
                                        fullWidth
                                        href={p.loginUrl}
                                        id={`social-${p.alias}`}
                                        startIcon={p.iconClasses ? <i className={p.iconClasses} aria-hidden="true"></i> : null}
                                        sx={{
                                            textTransform: "none",
                                            borderColor: "divider",
                                            color: "text.primary",
                                            "&:hover": {
                                                borderColor: "text.primary",
                                                backgroundColor: "action.hover"
                                            }
                                        }}
                                    >
                                        <span dangerouslySetInnerHTML={{ __html: kcSanitize(p.displayName) }}></span>
                                    </Button>
                                ))}
                            </Box>
                        </Box>
                    )}
                </>
            }
        >
            <Box id="kc-form">
                <Box id="kc-form-wrapper">
                    {realm.password && (
                        <form
                            id="kc-form-login"
                            onSubmit={() => {
                                setIsLoginButtonDisabled(true);
                                return true;
                            }}
                            action={url.loginAction}
                            method="post"
                        >
                            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                                {!usernameHidden && (
                                    <TextField
                                        id="username"
                                        name="username"
                                        label={
                                            !realm.loginWithEmailAllowed
                                                ? msg("username")
                                                : !realm.registrationEmailAsUsername
                                                  ? msg("usernameOrEmail")
                                                  : msg("email")
                                        }
                                        defaultValue={login.username ?? ""}
                                        autoFocus
                                        autoComplete="username"
                                        error={messagesPerField.existsError("username", "password")}
                                        helperText={
                                            messagesPerField.existsError("username", "password") && (
                                                <span
                                                    dangerouslySetInnerHTML={{
                                                        __html: kcSanitize(messagesPerField.getFirstError("username", "password"))
                                                    }}
                                                />
                                            )
                                        }
                                        fullWidth
                                        InputProps={{
                                            startAdornment: (
                                                <InputAdornment position="start">
                                                    <User size={20} />
                                                </InputAdornment>
                                            )
                                        }}
                                    />
                                )}

                                <TextField
                                    id="password"
                                    name="password"
                                    label={msg("password")}
                                    type={isPasswordRevealed ? "text" : "password"}
                                    autoComplete="current-password"
                                    error={messagesPerField.existsError("username", "password")}
                                    helperText={
                                        usernameHidden &&
                                        messagesPerField.existsError("username", "password") && (
                                            <span
                                                dangerouslySetInnerHTML={{
                                                    __html: kcSanitize(messagesPerField.getFirstError("username", "password"))
                                                }}
                                            />
                                        )
                                    }
                                    fullWidth
                                    InputProps={{
                                        startAdornment: (
                                            <InputAdornment position="start">
                                                <Lock size={20} />
                                            </InputAdornment>
                                        ),
                                        endAdornment: (
                                            <InputAdornment position="end">
                                                <IconButton
                                                    aria-label={msgStr(isPasswordRevealed ? "hidePassword" : "showPassword")}
                                                    onClick={() => setIsPasswordRevealed(!isPasswordRevealed)}
                                                    edge="end"
                                                >
                                                    {isPasswordRevealed ? <EyeOff size={20} /> : <Eye size={20} />}
                                                </IconButton>
                                            </InputAdornment>
                                        )
                                    }}
                                />

                                <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                                    {realm.rememberMe && !usernameHidden && (
                                        <FormControlLabel
                                            control={<Checkbox id="rememberMe" name="rememberMe" defaultChecked={!!login.rememberMe} />}
                                            label={msg("rememberMe")}
                                        />
                                    )}

                                    {realm.resetPasswordAllowed && (
                                        <Link href={url.loginResetCredentialsUrl} variant="body2" underline="hover">
                                            {msg("doForgotPassword")}
                                        </Link>
                                    )}
                                </Box>

                                <input type="hidden" id="id-hidden-input" name="credentialId" value={auth.selectedCredential} />

                                <Button
                                    fullWidth
                                    variant="contained"
                                    size="large"
                                    type="submit"
                                    disabled={isLoginButtonDisabled}
                                    name="login"
                                    sx={{ mt: 1 }}
                                >
                                    {msgStr("doLogIn")}
                                </Button>
                            </Box>
                        </form>
                    )}
                </Box>
            </Box>
        </Template>
    );
}
