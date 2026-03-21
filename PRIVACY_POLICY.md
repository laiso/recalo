# Privacy Policy for Recalo

Last updated: March 21, 2026

## Overview

Recalo is an Android application that analyzes photos of your meals using AI to extract nutritional information and syncs it with Google Health Connect. This privacy policy explains how we handle your data.

## Data Collection

Recalo does NOT collect, store, or transmit any personal data to our servers.

### What We Don't Collect
- No personal information
- No meal photos stored on our servers
- No analytics or tracking
- No usage data transmitted externally

## How the App Works

1. **Camera/Gallery**: You take a photo of your meal or select an existing image
2. **AI Processing**: The image is sent directly to your chosen AI provider (OpenAI API)
3. **Local Storage**: Meal records are stored locally on your device using Room database
4. **Health Connect**: Nutritional data is written to Google Health Connect on your device

## API Keys

- API keys are stored locally on your device
- Keys are only used to communicate with OpenAI API
- We do not store or have access to your API keys
- You bring your own API key (BYOK model)

## Permissions

### CAMERA
Used to take photos of your meals for nutritional analysis. This permission is **optional** - you can also select images from your gallery instead.

### POST_NOTIFICATIONS
Used to show notifications (if applicable).

### INTERNET
Required to communicate with OpenAI API for AI analysis.

## Health Connect Integration

- The app writes nutritional data to Google Health Connect
- Health Connect data is stored locally and managed by Google
- The app only writes data - it does not read other health data
- Google's privacy policy applies to Health Connect data

## Third-Party Services

When you use the AI analysis feature, meal photos are sent to:
- **OpenAI API**: For nutritional analysis using GPT models

OpenAI's privacy policy applies: https://openai.com/privacy

## Local Storage

- All meal history and images are stored locally on your device
- Data is stored using Room database
- No data is transmitted to our servers
- You can delete all data by clearing the app's storage

## Data Security

- All data stays on your device
- API keys are stored securely in app preferences
- No data is sold or shared with third parties (except OpenAI API for analysis)

## Children's Privacy

Recalo is not intended for users under 13 years of age. We do not knowingly collect data from children.

## Changes to This Policy

We may update this privacy policy. Changes will be reflected in this document with an updated date.

## Contact

For privacy concerns, please contact: support@lai.so

## App Information

- **App Name**: Recalo
- **Package Name**: so.lai.recalo
- **Developer**: Recalo Team
- **Source Code**: https://github.com/laiso/recalo

## Compliance

Recalo complies with:
- Google Play Developer Program Policies
- GDPR (no personal data collection)
- CCPA (no sale of personal information)
