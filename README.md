# Nova Browser

A professional, production-ready Android browser application built with Kotlin and Jetpack Compose.

## Features

- **Modern UI**: Built with Jetpack Compose and Material Design 3
- **Fast WebView**: Optimized WebView with hardware acceleration
- **Bookmarks**: Save and organize your favorite websites
- **History**: Track your browsing history
- **Incognito Mode**: Private browsing without saving history
- **Multi-tab Support**: Browse multiple websites simultaneously
- **Search Engines**: Choose from Google, DuckDuckGo, Bing, and Yahoo
- **Download Manager**: Download files with progress tracking
- **Settings**: Customize your browsing experience
- **Security**: Production-ready security configurations

## Architecture

- **MVVM**: Model-View-ViewModel architecture
- **Room**: Local database for bookmarks, history, and tabs
- **Koin**: Dependency injection
- **Coroutines & Flow**: Asynchronous programming
- **Material Design 3**: Modern UI components

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM
- **Database**: Room
- **Dependency Injection**: Koin
- **Async Programming**: Kotlin Coroutines & Flow
- **WebView**: Android WebView with WebKit
- **Build Tool**: Gradle with Kotlin DSL

## Project Structure

```
app/
├── src/main/java/com/technova/browser/
│   ├── data/
│   │   ├── local/
│   │   │   ├── dao/          # Database access objects
│   │   │   ├── database/     # Room database
│   │   │   ├── entity/       # Database entities
│   │   │   └── typeconverters/ # Room type converters
│   │   ├── model/           # Domain models
│   │   └── repository/      # Data repositories
│   ├── di/                  # Dependency injection
│   ├── ui/
│   │   ├── components/      # Reusable UI components
│   │   ├── navigation/      # Navigation setup
│   │   ├── screens/         # Screen composables
│   │   └── theme/           # Theming
│   ├── util/                # Utilities
│   ├── viewmodel/           # ViewModels
│   ├── MainActivity.kt      # Main activity
│   └── NovaBrowserApplication.kt # Application class
└── src/main/res/            # Resources
```

## Requirements

- **Android Studio**: Arctic Fox or later
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Kotlin**: 1.9.22+
- **Java**: 17+

## Setup

1. Clone the repository:
```bash
git clone https://github.com/your-username/nova-browser.git
cd nova-browser
```

2. Open in Android Studio and sync the project

3. Build and run on device/emulator

## Configuration

### Signing (for release builds)

Create `app/keystore.properties`:
```properties
storeFile=../keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

### GitHub Actions Secrets

For CI/CD, set these secrets in your repository:
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_STORE_PASSWORD`
- `GITHUB_TOKEN` (automatically provided)

## Building

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Run Tests
```bash
./gradlew testDebugUnitTest
```

### Lint Check
```bash
./gradlew lintDebug
```

## CI/CD

The project uses GitHub Actions for continuous integration and deployment:

- **Test**: Runs unit tests
- **Build**: Builds debug and release APKs
- **Lint**: Code quality checks
- **Analyze**: Static code analysis with Detekt
- **Release**: Creates GitHub releases for production builds

## Security

- **Network Security**: Cleartext traffic disabled in production
- **WebView Security**: Strict security settings
- **ProGuard**: Code obfuscation for release builds
- **Permissions**: Minimal required permissions
- **Data Storage**: Sensitive data encrypted

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Material Design 3 for the design system
- Android WebView for web rendering
- Jetpack Compose for modern UI development
- Room for local data persistence
- Koin for dependency injection

## Roadmap

- [ ] Tab management UI
- [ ] Download manager implementation
- [ ] Settings screen
- [ ] Incognito mode
- [ ] Advanced privacy controls
- [ ] Extension support
- [ ] Desktop sync
- [ ] Performance optimizations