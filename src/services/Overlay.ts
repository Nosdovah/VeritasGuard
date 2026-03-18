
import { NativeModules } from 'react-native';

const { OverlayModule } = NativeModules;

interface OverlayInterface {
    showBadge(level: 'safe' | 'warning' | 'danger' | 'info', message: string): Promise<void>;
    dismissBadge(): Promise<void>;
    canDrawOverlays(): Promise<boolean>;
}

export default OverlayModule as OverlayInterface;
