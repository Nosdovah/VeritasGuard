
import { AppRegistry } from 'react-native';
import OverlayModule from './Overlay';

const PhishingDetector = async (taskData: any) => {
    console.log('Headless JS Task running: PhishingDetector');
    console.log('Clipboard content:', taskData.clipboardText);

    const { clipboardText } = taskData;

    if (!clipboardText) return;

    // Simple regex for URL detection
    const urlRegex = /(https?:\/\/[^\s]+)/g;
    const urls = clipboardText.match(urlRegex);

    if (urls && urls.length > 0) {
        console.log('URL detected:', urls[0]);

        // Mock Phishing Check
        if (urls[0].includes('suspicious') || urls[0].includes('login')) {
            console.warn('⚠️ POTENTIAL PHISHING URL DETECTED: ' + urls[0]);
            OverlayModule.showBadge('danger', 'Phishing URL Detected!\n' + urls[0]);
        } else {
            console.log('URL seems safe (mock check).');
            // Optional: Show safe badge briefly
            // OverlayModule.showBadge('safe', 'URL Verified: Safe');
        }
    }
};

export default PhishingDetector;
