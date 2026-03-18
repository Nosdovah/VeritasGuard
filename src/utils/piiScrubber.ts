
export interface ScrubResult {
  originalText: string;
  scrubbedText: string;
  piiTypes: string[];
  piiCount: number;
}

export class PiiScrubber {
  static scrub(text: string): ScrubResult {
    let scrubbed = text;
    const types: string[] = [];
    let count = 0;

    // Email
    const emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;
    if (emailRegex.test(scrubbed)) {
      scrubbed = scrubbed.replace(emailRegex, '[EMAIL_REDACTED]');
      types.push('Email');
      count++;
    }

    // Phone (Simple)
    const phoneRegex = /\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/g;
    if (phoneRegex.test(scrubbed)) {
      scrubbed = scrubbed.replace(phoneRegex, '[PHONE_REDACTED]');
      types.push('Phone');
      count++;
    }

    return {
      originalText: text,
      scrubbedText: scrubbed,
      piiTypes: types,
      piiCount: count
    };
  }
}
