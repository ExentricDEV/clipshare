import { Box, Button, Container, Typography } from "@mui/material";
import { FileQuestion, Home } from "lucide-react";
import { useNavigate } from "react-router";

export default function NotFound() {
  const navigate = useNavigate();

  return (
    <Container maxWidth="md">
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
          textAlign: "center",
          gap: 3,
        }}
      >
        <Box
          sx={{
            backgroundColor: "rgba(25, 118, 210, 0.08)",
            borderRadius: "50%",
            p: 4,
            mb: 2,
          }}
        >
          <FileQuestion size={64} color="#1976d2" />
        </Box>

        <Typography
          variant="h2"
          component="h1"
          fontWeight="bold"
          color="text.primary"
        >
          404
        </Typography>

        <Typography variant="h5" color="text.secondary" sx={{ mb: 2 }}>
          Page not found
        </Typography>

        <Typography
          variant="body1"
          color="text.secondary"
          sx={{ maxWidth: 500, mb: 2 }}
        >
          The page you are looking for might have been removed, had its name
          changed, or is temporarily unavailable.
        </Typography>

        <Button
          variant="contained"
          size="large"
          startIcon={<Home size={20} />}
          onClick={() => navigate("/")}
          sx={{
            textTransform: "none",
            px: 4,
            py: 1.5,
            borderRadius: 2,
          }}
        >
          Go to Home
        </Button>
      </Box>
    </Container>
  );
}
