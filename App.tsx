/**
 * VeritasGuard — Main Application Entry Point
 *
 * Responsibilities:
 *   1. Unified Dashboard: merges Onboarding + Main UI.
 *   2. Real-time Permission Monitoring: Uses AppState to auto-update status.
 *   3. Settings Hub: Toggles for Monitoring, Dark Mode, Premium.
 */

import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
    StyleSheet,
    View,
    Text,
    TouchableOpacity,
    ScrollView,
    Alert,
    NativeModules,
    Platform,
    Linking,
    StatusBar,
    DeviceEventEmitter,
    Animated,
    Dimensions,
    Easing,
    AppState,
    PermissionsAndroid,
    Switch,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Clipboard from '@react-native-clipboard/clipboard';
import { hoaxChecker } from './src/services/hoaxChecker';
import type { HoaxCheckResult } from './src/services/apiClient';
import type { ScrubResult } from './src/utils/piiScrubber';

const { ClipboardListenerModule, OverlayModule } = NativeModules;
const { width } = Dimensions.get('window');

// ============================================================================
// Types
// ============================================================================

interface SecurityEvent {
    id: string;
    type: 'phishing' | 'hoax' | 'pii' | 'system';
    level: 'safe' | 'warning' | 'danger' | 'info';
    message: string;
    timestamp: Date;
    source?: string;
}

interface PermissionState {
    overlay: boolean;
    accessibility: boolean;
    notification: boolean;
}

type Language = 'English' | 'Indonesian' | 'Korean' | 'Malay' | 'Thai';

// ============================================================================
// App Component
// ============================================================================

const App: React.FC = () => {
    // Logic State
    const [isMonitoring, setIsMonitoring] = useState(false);
    const [events, setEvents] = useState<SecurityEvent[]>([]);
    const [permissions, setPermissions] = useState<PermissionState>({
        overlay: false,
        accessibility: false,
        notification: false,
    });
    const [appState, setAppState] = useState(AppState.currentState);

    // Derived State
    const allPermissionsGranted = permissions.overlay && permissions.accessibility && permissions.notification;

    // UI State
    const [language, setLanguage] = useState<Language>('English');
    const [isDarkMode, setIsDarkMode] = useState(true); // Default to "Deep Blue"
    const [isPremium, setIsPremium] = useState(false);

    // Persistence Check (TC-003)
    useEffect(() => {
        const loadSettings = async () => {
            try {
                const savedLang = await AsyncStorage.getItem('user_language');
                if (savedLang) setLanguage(savedLang as Language);
            } catch (e) {
                console.warn('Failed to load settings', e);
            }
        };
        loadSettings();
    }, []);

    const changeLanguage = async (lang: Language) => {
        setLanguage(lang);
        await AsyncStorage.setItem('user_language', lang);
    };

    // Animation Values
    const fadeAnim = useRef(new Animated.Value(0)).current;
    const bubble1Anim = useRef(new Animated.Value(0)).current;
    const bubble2Anim = useRef(new Animated.Value(0)).current;
    const logoScale = useRef(new Animated.Value(0.8)).current;

    // ==========================================================================
    // Lifecycle & Animations
    // ==========================================================================

    const checkPermissions = useCallback(async () => {
        try {
            // 1. Overlay
            const overlay = await OverlayModule.canDrawOverlays();

            // 2. Accessibility
            let accessibility = false;
            try {
                accessibility = await OverlayModule.isAccessibilityServiceEnabled();
            } catch (e) {
                console.warn("isAccessibilityServiceEnabled check failed", e);
            }

            // 3. Notification
            let notification = true;
            if (Platform.OS === 'android' && Platform.Version >= 33) {
                notification = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
            }

            setPermissions({ overlay, accessibility, notification });

        } catch (e) {
            console.warn('Permission check failed', e);
        }
    }, []);

    useEffect(() => {
        checkPermissions();

        const subscription = AppState.addEventListener('change', nextAppState => {
            if (appState.match(/inactive|background/) && nextAppState === 'active') {
                checkPermissions();
            }
            setAppState(nextAppState);
        });

        // Start Animations
        Animated.parallel([
            Animated.timing(fadeAnim, {
                toValue: 1,
                duration: 1000,
                useNativeDriver: true,
            }),
            Animated.spring(logoScale, {
                toValue: 1,
                friction: 6,
                useNativeDriver: true,
            }),
            Animated.loop(
                Animated.sequence([
                    Animated.timing(bubble1Anim, {
                        toValue: 1,
                        duration: 4000,
                        easing: Easing.inOut(Easing.ease),
                        useNativeDriver: true,
                    }),
                    Animated.timing(bubble1Anim, {
                        toValue: 0,
                        duration: 4000,
                        easing: Easing.inOut(Easing.ease),
                        useNativeDriver: true,
                    }),
                ])
            ),
            Animated.loop(
                Animated.sequence([
                    Animated.timing(bubble2Anim, {
                        toValue: 1,
                        duration: 5000,
                        easing: Easing.inOut(Easing.ease),
                        useNativeDriver: true,
                    }),
                    Animated.timing(bubble2Anim, {
                        toValue: 0,
                        duration: 5000,
                        easing: Easing.inOut(Easing.ease),
                        useNativeDriver: true,
                    }),
                ])
            ),
        ]).start();

        return () => {
            subscription.remove();
        };
    }, [appState, checkPermissions, fadeAnim, bubble1Anim, bubble2Anim, logoScale]);

    // ==========================================================================
    // Service Initialization
    // ==========================================================================

    const toggleMonitoring = useCallback(async () => {
        if (isMonitoring) {
            // Stop
            try {
                await ClipboardListenerModule.stopListening();
                hoaxChecker.stop();
                setIsMonitoring(false);
                addSystemEvent('warning', 'Security monitoring deactivated');
            } catch (error) {
                console.error('Failed to stop:', error);
            }
        } else {
            // Start
            if (!allPermissionsGranted) {
                Alert.alert('Permissions Required', 'Please enable all permissions above first.');
                return;
            }
            try {
                await ClipboardListenerModule.startListening();

                // Overlay Listener
                DeviceEventEmitter.removeAllListeners('onOverlayAction');
                DeviceEventEmitter.addListener('onOverlayAction', async (action) => {
                    handleOverlayAction(action);
                });

                hoaxChecker.start({
                    onResult: (result, pkg) => addHoaxEvent(result, pkg),
                    onError: (err, pkg) => console.error(err),
                    onPIIDetected: (res, pkg) => addPIIEvent(res, pkg),
                });

                setIsMonitoring(true);
                addSystemEvent('safe', 'Security monitoring activated');
            } catch (error) {
                console.error('Failed to start:', error);
                Alert.alert('Error', 'Failed to start. Please restart app.');
            }
        }
    }, [isMonitoring, allPermissionsGranted]);

    const handleOverlayAction = async (action: string) => {
        if (action === 'scan_phishing') {
            const content = await Clipboard.getString();
            if (content && (content.includes('http') || content.includes('www'))) {
                // Basic mock logic for demo
                if (content.includes('suspicious') || content.includes('login')) {
                    OverlayModule.showBadge('danger', 'Waspada! Link ini terindikasi Hoax/Penipuan.');
                } else {
                    OverlayModule.showBadge('safe', 'Selamat! Informasi ini terverifikasi benar.');
                }
            } else {
                OverlayModule.showBadge('info', 'No link detected in clipboard.');
            }
        }
    };

    const addSystemEvent = (level: SecurityEvent['level'], message: string) => {
        const newEvent: SecurityEvent = {
            id: Date.now().toString(),
            type: 'system',
            level,
            message,
            timestamp: new Date(),
        };
        setEvents(prev => [newEvent, ...prev].slice(0, 50));
    };

    const addHoaxEvent = (result: HoaxCheckResult, pkg: string) => {
        const level = result.verdict === 'false' ? 'danger' : result.verdict === 'unverified' ? 'warning' : 'safe';
        const newEvent: SecurityEvent = {
            id: Date.now().toString(),
            type: 'hoax',
            level,
            message: `${result.verdict.toUpperCase()}: ${result.explanation.substring(0, 80)}...`,
            timestamp: new Date(),
            source: pkg,
        };
        setEvents(prev => [newEvent, ...prev].slice(0, 50));
    };

    const addPIIEvent = (result: ScrubResult, pkg: string) => {
        const newEvent: SecurityEvent = {
            id: Date.now().toString(),
            type: 'pii',
            level: 'info',
            message: `PII Scrubbed: ${result.piiCount} items`,
            timestamp: new Date(),
            source: pkg,
        };
        setEvents(prev => [newEvent, ...prev].slice(0, 50));
    };


    // ==========================================================================
    // Requests
    // ==========================================================================

    const requestOverlay = () => Linking.sendIntent('android.settings.action.MANAGE_OVERLAY_PERMISSION');
    const requestAccessibility = () => Linking.sendIntent('android.settings.ACCESSIBILITY_SETTINGS');
    const requestNotification = async () => {
        if (Platform.OS === 'android' && Platform.Version >= 33) {
            await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
            checkPermissions();
        }
    };

    // ==========================================================================
    // Render Helpers
    // ==========================================================================



    // Theme Colors
    const theme = {
        bg: isDarkMode ? '#0B2D72' : '#FFFFFF',
        text: isDarkMode ? '#EEEEEE' : '#121212',
        accent: '#AECC0F', // Green accent
        secondary: '#91938F',
        card: isDarkMode ? 'rgba(255, 255, 255, 0.05)' : '#F5F5F5',
        cardBorder: isDarkMode ? 'rgba(174, 204, 223, 0.2)' : '#E0E0E0',
    };

    const getLevelColor = (level: string) => {
        switch (level) {
            case 'safe': return '#AECC0F';
            case 'warning': return '#F59E0B';
            case 'danger': return '#EF4444';
            case 'info': return '#3B82F6';
            default: return theme.secondary;
        }
    };

    // Interpolations
    const bubbleScale = bubble1Anim.interpolate({ inputRange: [0, 1], outputRange: [1, 1.2] });

    return (
        <View style={[styles.container, { backgroundColor: theme.bg }]}>
            <StatusBar barStyle={isDarkMode ? "light-content" : "dark-content"} backgroundColor={theme.bg} />

            {/* Background Bubbles (only visible in Dark Mode for effect) */}
            {isDarkMode && (
                <>
                    <Animated.View style={[styles.bubble, { top: -50, right: -50, transform: [{ scale: bubbleScale }] }]} />
                    <Animated.View style={[styles.bubble, { bottom: -100, left: -50, width: 300, height: 300 }]} />
                </>
            )}

            <ScrollView contentContainerStyle={styles.scrollContent}>

                {/* 1. WELCOME SCREEN HEADER */}
                <View style={styles.header}>
                    <Animated.View style={[styles.logoContainer, { transform: [{ scale: logoScale }] }]}>
                        <View style={styles.logoCircle}>
                            <Text style={styles.logoText}>🛡️</Text>
                        </View>
                    </Animated.View>
                    <Text style={[styles.greeting, { color: theme.text }]}>Hello!</Text>
                    <Text style={[styles.headerTitle, { color: theme.text }]}>VeritasGuard</Text>
                </View>

                <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.cardBorder }]}>
                    <View style={styles.rowBetween}>
                        <Text style={[styles.sectionTitle, { color: theme.secondary, marginBottom: 0 }]}>Setup & Language</Text>
                        <TouchableOpacity onPress={() => { checkPermissions(); Alert.alert("Status Refreshed", "Permission status updated."); }}>
                            <Text style={{ color: theme.accent, fontSize: 12 }}>↻ Refresh</Text>
                        </TouchableOpacity>
                    </View>
                    <View style={{ height: 12 }} />

                    {/* Language Mock Selector */}
                    <View style={styles.rowBetween}>
                        <Text style={{ color: theme.text }}>Language</Text>
                        <TouchableOpacity onPress={() => {
                            const langs: Language[] = ['English', 'Indonesian', 'Korean', 'Malay', 'Thai'];
                            const nextIndex = (langs.indexOf(language) + 1) % langs.length;
                            changeLanguage(langs[nextIndex]);
                        }}>
                            <Text style={{ color: theme.accent, fontWeight: 'bold' }}>{language} ▼</Text>
                        </TouchableOpacity>
                    </View>

                    {/* Permission Status / Enable Button */}
                    <View style={{ marginTop: 16 }}>
                        {!allPermissionsGranted ? (
                            <View>
                                <Text style={{ color: theme.secondary, marginBottom: 8, fontSize: 12 }}>
                                    App needs permissions to display floating bubble.
                                </Text>
                                <TouchableOpacity
                                    style={styles.primaryButton}
                                    onPress={() => {
                                        if (!permissions.overlay) requestOverlay();
                                        else if (!permissions.accessibility) requestAccessibility();
                                        else requestNotification();
                                    }}
                                >
                                    <Text style={styles.buttonText}>Enable Floating Bubble</Text>
                                </TouchableOpacity>
                                {/* Mini Status Indicators */}
                                <View style={styles.miniStatusRow}>
                                    <Text style={{ color: permissions.overlay ? theme.accent : '#EF4444', fontSize: 10 }}>1. Overlay {permissions.overlay ? '✓' : '✗'}</Text>
                                    <Text style={{ color: permissions.accessibility ? theme.accent : '#EF4444', fontSize: 10, marginHorizontal: 8 }}>2. Accessibility {permissions.accessibility ? '✓' : '✗'}</Text>
                                    <Text style={{ color: permissions.notification ? theme.accent : '#EF4444', fontSize: 10 }}>3. Notify {permissions.notification ? '✓' : '✗'}</Text>
                                </View>
                            </View>
                        ) : (
                            <Text style={{ color: theme.accent, fontWeight: 'bold', textAlign: 'center' }}>✓ All Permissions Granted</Text>
                        )}
                    </View>
                </View>

                {/* 3. MAIN SETTINGS HUB */}
                <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.cardBorder, opacity: allPermissionsGranted ? 1 : 0.5 }]}>
                    <Text style={[styles.sectionTitle, { color: theme.secondary }]}>Settings Hub</Text>

                    {/* Monitor Toggle */}
                    <View style={styles.rowBetween}>
                        <View>
                            <Text style={[styles.settingLabel, { color: theme.text }]}>Monitor Activity</Text>
                            <Text style={{ color: theme.secondary, fontSize: 12 }}>Enable floating assistant</Text>
                        </View>
                        <Switch
                            value={isMonitoring}
                            onValueChange={toggleMonitoring}
                            trackColor={{ false: "#767577", true: theme.accent }}
                            thumbColor={isMonitoring ? "#FFFFFF" : "#f4f3f4"}
                            disabled={!allPermissionsGranted}
                        />
                    </View>

                    <View style={styles.divider} />

                    {/* Dark Mode Toggle */}
                    <View style={styles.rowBetween}>
                        <Text style={[styles.settingLabel, { color: theme.text }]}>Dark Mode</Text>
                        <Switch
                            value={isDarkMode}
                            onValueChange={setIsDarkMode}
                            trackColor={{ false: "#767577", true: theme.accent }}
                        />
                    </View>
                </View>

                {/* Premium Banner */}
                {!isPremium && (
                    <TouchableOpacity style={styles.premiumBanner} onPress={() => Alert.alert("Premium", "Upgrade to remove ads!")}>
                        <Text style={styles.premiumText}>💎 Remove Ads? Go Premium (IDR 15,000/mo)</Text>
                    </TouchableOpacity>
                )}

                {/* Event Log (For Verification) */}
                <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.cardBorder }]}>
                    <Text style={[styles.sectionTitle, { color: theme.secondary }]}>Recent Activity</Text>
                    {events.length === 0 ? (
                        <Text style={{ color: theme.secondary, fontStyle: 'italic', textAlign: 'center', padding: 10 }}>No activity logged.</Text>
                    ) : (
                        events.slice(0, 5).map(e => (
                            <View key={e.id} style={{ marginBottom: 8, borderBottomWidth: 0.5, borderBottomColor: 'rgba(255,255,255,0.1)', paddingBottom: 4 }}>
                                <Text style={{ color: getLevelColor(e.level), fontWeight: 'bold' }}>{e.type.toUpperCase()}</Text>
                                <Text style={{ color: theme.text, fontSize: 12 }}>{e.message}</Text>
                            </View>
                        ))
                    )}
                </View>

            </ScrollView>
        </View>
    );
};

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    scrollContent: {
        padding: 24,
        paddingBottom: 40,
    },
    bubble: {
        position: 'absolute',
        backgroundColor: '#AECC0F',
        borderRadius: 150,
        opacity: 0.1,
        width: 250,
        height: 250
    },
    header: {
        alignItems: 'center',
        marginTop: 40,
        marginBottom: 30,
    },
    logoContainer: {
        marginBottom: 10,
        shadowColor: '#AECC0F',
        shadowOffset: { width: 0, height: 0 },
        shadowOpacity: 0.5,
        shadowRadius: 20,
        elevation: 10,
    },
    logoCircle: {
        width: 80,
        height: 80,
        borderRadius: 40,
        backgroundColor: '#000000',
        justifyContent: 'center',
        alignItems: 'center',
        borderWidth: 2,
        borderColor: '#AECC0F',
    },
    logoText: {
        fontSize: 40,
    },
    greeting: {
        fontSize: 18,
        fontWeight: 'normal',
        marginBottom: 4,
        opacity: 0.8
    },
    headerTitle: {
        fontSize: 28,
        fontWeight: '800',
        letterSpacing: 1,
        textTransform: 'uppercase',
    },

    // Cards
    card: {
        borderRadius: 16,
        padding: 16,
        marginBottom: 16,
        borderWidth: 1,
    },
    sectionTitle: {
        fontSize: 12,
        fontWeight: '700',
        marginBottom: 12,
        textTransform: 'uppercase',
        letterSpacing: 1,
    },
    rowBetween: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
    },
    settingLabel: {
        fontSize: 16,
        fontWeight: '600',
    },
    divider: {
        height: 1,
        backgroundColor: 'rgba(255,255,255,0.1)',
        marginVertical: 12,
    },

    // Buttons
    primaryButton: {
        backgroundColor: '#AECC0F',
        borderRadius: 8,
        paddingVertical: 12,
        alignItems: 'center',
        marginTop: 8,
    },
    buttonText: {
        color: '#121212', // Dark text on Green
        fontWeight: 'bold',
        fontSize: 14,
    },
    miniStatusRow: {
        flexDirection: 'row',
        justifyContent: 'center',
        marginTop: 12,
    },

    // Premium
    premiumBanner: {
        backgroundColor: 'rgba(174, 204, 15, 0.2)',
        padding: 12,
        borderRadius: 8,
        marginBottom: 16,
        borderWidth: 1,
        borderColor: '#AECC0F',
        alignItems: 'center'
    },
    premiumText: {
        color: '#AECC0F',
        fontWeight: 'bold',
        fontSize: 12,
    }
});

export default App;
