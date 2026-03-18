
export interface HoaxCheckResult {
    verdict: 'true' | 'false' | 'likely_true' | 'likely_false' | 'unverified';
    confidence: number;
    explanation: string;
    sources: string[];
}

export class ApiClient {
    static async checkHoax(text: string): Promise<HoaxCheckResult> {
        // Placeholder for real backend call
        return {
            verdict: 'unverified',
            confidence: 0,
            explanation: 'Backend not connected',
            sources: []
        };
    }
}
