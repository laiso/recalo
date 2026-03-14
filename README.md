# Caroli AI

Caroli AI is an Android application that uses OpenAI's Vision capabilities to analyze photos of your meals and automatically extract nutritional information (Calories, Protein, Fat, Carbohydrates) and syncs it with Google Health Connect.

> Note: This app is currently in its initial release phase. It is primarily intended for developers and early adopters who can build the project themselves.

## Demo

![Caroli AI Demo](./apps/android/docs/images/demo.gif)

*AI analyzes your meal and syncs to Health Connect in seconds. (Processing time is sped up for the demo)*

## Features

* AI-Powered Nutrition Analysis: Take a photo or select an image from your gallery, and the app uses OpenAI (gpt-4o-mini / gpt-4o) to estimate the nutritional breakdown and identify specific food items in the picture.
* Health Connect Integration: Seamlessly writes your daily meal records directly to Google Health Connect, allowing you to centralize your nutrition data alongside other fitness metrics.
* Local First Storage: All meal histories and images are stored securely on your device using Room database.
* Bring Your Own Key (BYOK): To use the AI features, you simply input your own OpenAI API key.

## Getting Started

### Prerequisites

* Android Studio (Koala or newer recommended)
* An Android device or emulator running Android 14+ (API 34+) for Health Connect compatibility.
* An OpenAI API Key.

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/laiso/recalo.git
   cd recalo
   ```

2. Open the apps/android directory in Android Studio.

3. Sync the Gradle project.

4. Run the app (./gradlew assembleDebug or click the Run button in Android Studio).

### Setup in the App

1. Launch the app on your device.
2. Tap the Settings (Gear icon) in the top right corner.
3. Paste your OpenAI API Key.
4. (Optional) Adjust the AI Model quality or the start time for your daily summaries.
5. Tap Connect on the home screen to grant the app write permissions to Google Health Connect.

## Tech Stack

* Language: Kotlin
* UI Toolkit: Jetpack Compose (Material Design 3)
* Architecture: Clean Architecture (Presentation, Domain, Data) with MVVM
* Local Database: Room
* Health Integration: Health Connect API
* Network: Retrofit / OkHttp (for OpenAI API)
* Image Loading: Coil
* Asynchronous Programming: Kotlin Coroutines & Flow

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project.
2. Create your feature branch (git checkout -b feature/AmazingFeature).
3. Commit your changes (git commit -m 'Add some AmazingFeature').
4. Push to the branch (git push origin feature/AmazingFeature).
5. Open a Pull Request.

## License

This project is licensed under the GNU General Public License v3.0 (GPLv3).

You are free to use, modify, and distribute this software, but any derived works must also be open-source and distributed under the same GPLv3 license. See the LICENSE file for more details.
