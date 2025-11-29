import { useEffect, useState, Fragment } from "react";
import type { UserProfileFormFieldsProps } from "keycloakify/login/UserProfileFormFieldsProps";
import type { KcContext } from "./KcContext";
import type { I18n } from "./i18n";
import { Box, TextField, InputAdornment, IconButton } from "@mui/material";
import { Eye, EyeOff } from "lucide-react";
import { useUserProfileForm } from "keycloakify/login/lib/useUserProfileForm";

export default function UserProfileFormFields(props: UserProfileFormFieldsProps<KcContext, I18n>) {
    const { kcContext, i18n, onIsFormSubmittableValueChange, doMakeUserConfirmPassword } = props;
    const { advancedMsg } = i18n;

    const {
        formState: { formFieldStates, isFormSubmittable },
        dispatchFormAction
    } = useUserProfileForm({
        kcContext,
        i18n,
        doMakeUserConfirmPassword
    });

    useEffect(() => {
        onIsFormSubmittableValueChange(isFormSubmittable);
    }, [isFormSubmittable, onIsFormSubmittableValueChange]);

    const [isPasswordRevealed, setIsPasswordRevealed] = useState(false);
    const [isConfirmPasswordRevealed, setIsConfirmPasswordRevealed] = useState(false);

    return (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
            {formFieldStates.map(({ attribute, displayableErrors, valueOrValues }) => {
                const isPassword = attribute.name === "password" || attribute.name === "password-confirm";
                const isConfirmPassword = attribute.name === "password-confirm";

                const value = Array.isArray(valueOrValues) ? valueOrValues[0] : valueOrValues;

                return (
                    <Fragment key={attribute.name}>
                        <TextField
                            id={attribute.name}
                            name={attribute.name}
                            label={attribute.displayName ? advancedMsg(attribute.displayName) : attribute.name}
                            type={
                                isPassword
                                    ? isConfirmPassword
                                        ? isConfirmPasswordRevealed
                                            ? "text"
                                            : "password"
                                        : isPasswordRevealed
                                          ? "text"
                                          : "password"
                                    : "text"
                            }
                            value={value ?? ""}
                            required={attribute.required}
                            disabled={attribute.readOnly}
                            autoComplete={attribute.autocomplete}
                            error={displayableErrors.length > 0}
                            helperText={displayableErrors.length > 0 && <span>{displayableErrors[0].errorMessage}</span>}
                            fullWidth
                            onChange={event => {
                                dispatchFormAction({
                                    action: "update",
                                    name: attribute.name,
                                    valueOrValues: event.target.value
                                });
                            }}
                            onBlur={() => {
                                dispatchFormAction({
                                    action: "focus lost",
                                    name: attribute.name,
                                    fieldIndex: undefined
                                });
                            }}
                            InputLabelProps={{
                                shrink: true
                            }}
                            InputProps={{
                                endAdornment: isPassword ? (
                                    <InputAdornment position="end">
                                        <IconButton
                                            aria-label={
                                                isConfirmPassword
                                                    ? isConfirmPasswordRevealed
                                                        ? "Hide password"
                                                        : "Show password"
                                                    : isPasswordRevealed
                                                      ? "Hide password"
                                                      : "Show password"
                                            }
                                            onClick={() => {
                                                if (isConfirmPassword) {
                                                    setIsConfirmPasswordRevealed(!isConfirmPasswordRevealed);
                                                } else {
                                                    setIsPasswordRevealed(!isPasswordRevealed);
                                                }
                                            }}
                                            edge="end"
                                        >
                                            {isConfirmPassword ? (
                                                isConfirmPasswordRevealed ? (
                                                    <EyeOff size={20} />
                                                ) : (
                                                    <Eye size={20} />
                                                )
                                            ) : isPasswordRevealed ? (
                                                <EyeOff size={20} />
                                            ) : (
                                                <Eye size={20} />
                                            )}
                                        </IconButton>
                                    </InputAdornment>
                                ) : undefined
                            }}
                        />
                    </Fragment>
                );
            })}
        </Box>
    );
}
