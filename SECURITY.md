# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| v0.4.0  | :white_check_mark: |
| v0.3.0  | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in Wavelet, please report it responsibly.

**Do not** open a public issue for security vulnerabilities. Instead, contact the maintainer directly:

- **Email:** sololevel070@gmail.com
- **GitHub:** https://github.com/sololevel070

### What to Include

Please include as much detail as possible:
- Steps to reproduce the issue
- Affected versions
- Device model and Android version
- Any relevant logs or screenshots

### Response Timeline

- We will acknowledge your report within 48 hours
- We will provide an initial assessment within 7 days
- We will work with you on a fix and disclosure timeline

## Security Best Practices for Contributors

- All pull requests must be reviewed before merging
- Branch protection is enabled on the `main` branch
- CODEOWNERS file designates reviewers for all code changes
- Never commit passwords, API keys, or tokens to the repository
- Server credentials in the app are stored in app-private storage only

## Dependencies

Wavelet uses the following key dependencies:
- Material3, Navigation Compose, Media3 (ExoPlayer), Room, Coil, OkHttp, kotlinx-serialization
- Dependabot is enabled to alert about vulnerable dependencies
