/**
 * VeritasGuard — Application Entry Point
 *
 * Registers:
 *   1. Main App component
 *   2. PhishingDetectorTask as a Headless JS task
 *
 * The Headless JS task runs in a separate JS context (no UI) when
 * triggered by the native ClipboardListenerService.
 */

import { AppRegistry } from 'react-native';
import App from './App';
import PhishingDetectorTask from './src/services/PhishingDetector';
import { name as appName } from './app.json';

// Register the main application
AppRegistry.registerComponent(appName, () => App);

// Register the Headless JS task for background phishing detection
// This is invoked by PhishingTaskService.kt when clipboard changes
AppRegistry.registerHeadlessTask('PhishingDetectorTask', () => PhishingDetectorTask);
