import { useEffect } from "react";
import type { TemplateProps } from "keycloakify/login/TemplateProps";
import type { I18n } from "./i18n";
import type { KcContext } from "./KcContext";
import { Box, Paper, Typography, Alert } from "@mui/material";

export default function Template(props: TemplateProps<KcContext, I18n>) {
    const {
        displayInfo = false,
        displayMessage = true,
        headerNode,
        socialProvidersNode = null,
        infoNode = null,
        documentTitle,
        kcContext,
        i18n,
        children
    } = props;

    const { msgStr } = i18n;
    const { message, isAppInitiatedAction } = kcContext;

    useEffect(() => {
        document.title = documentTitle ?? msgStr("loginTitle", kcContext.realm.displayName);
    }, [documentTitle, kcContext.realm.displayName, msgStr]);

    return (
        <Box
            sx={{
                minHeight: "100vh",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: "linear-gradient(135deg, #f8fafc 0%, #cbd5e1 100%)", // Subtle slate gradient
                p: 2
            }}
        >
            <Paper
                elevation={4}
                sx={{
                    width: "100%",
                    maxWidth: "450px",
                    borderRadius: 3, // ~12px
                    p: 4,
                    display: "flex",
                    flexDirection: "column"
                }}
            >
                <Box sx={{ mb: 3, textAlign: "center" }}>
                    <Typography variant="h5" component="h1" fontWeight="bold" color="text.primary">
                        {headerNode}
                    </Typography>
                </Box>

                {/* Messages */}
                {displayMessage && message && (message.type !== "warning" || !isAppInitiatedAction) && (
                    <Alert severity={message.type === "error" ? "error" : message.type === "success" ? "success" : "info"} sx={{ mb: 2 }}>
                        <span dangerouslySetInnerHTML={{ __html: message.summary }}></span>
                    </Alert>
                )}

                {children}

                {socialProvidersNode}

                {displayInfo && <Box sx={{ mt: 2 }}>{infoNode}</Box>}
            </Paper>
        </Box>
    );
}
