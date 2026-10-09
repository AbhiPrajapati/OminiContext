import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Global HTTP error interceptor.
 * Catches common error responses and provides user-facing feedback:
 *  - 401 Unauthorized → redirect to login (when auth is implemented)
 *  - 429 Too Many Requests → rate limit warning
 *  - 413 Payload Too Large → content size warning
 *  - 503/504 Gateway Timeout → AI fallback notification
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let userMessage = 'An unexpected error occurred.';

      switch (error.status) {
        case 0:
          userMessage = '⚠️ Cannot reach the server. Please check if the backend is running.';
          showToast(userMessage, 'error');
          break;

        case 401:
          userMessage = '🔒 Session expired. Please log in again.';
          showToast(userMessage, 'warning');
          // Future: redirect to login page
          // window.location.href = '/login';
          break;

        case 413:
          const maxBytes = error.error?.maxBytes;
          const maxKb = maxBytes ? Math.round(maxBytes / 1024) : 50;
          userMessage = `📦 Content too large. Maximum allowed size is ${maxKb} KB (~${Math.round(maxKb / 3.3)}K tokens). Please reduce your content.`;
          showToast(userMessage, 'warning');
          break;

        case 429:
          const retryAfter = error.error?.retryAfterSeconds || 60;
          userMessage = `⏳ Rate limit exceeded. Please wait ${retryAfter} seconds before trying again.`;
          showToast(userMessage, 'warning');
          break;

        case 503:
        case 504:
          userMessage = '🤖 AI distillation service is temporarily unavailable. Rule-based NLP compression was used as a fallback.';
          showToast(userMessage, 'info');
          break;

        default:
          if (error.status >= 400 && error.status < 500) {
            const detail = error.error?.detail || error.message;
            userMessage = `❌ Request failed: ${detail}`;
            showToast(userMessage, 'error');
          } else if (error.status >= 500) {
            const traceId = error.error?.traceId || '';
            userMessage = `💥 Server error occurred.${traceId ? ' Trace ID: ' + traceId : ''}`;
            showToast(userMessage, 'error');
          }
          break;
      }

      console.error(`[HTTP ${error.status}] ${req.method} ${req.url}`, error.error);
      return throwError(() => error);
    })
  );
};

/**
 * Simple toast notification system.
 * Creates floating toast messages that auto-dismiss.
 */
function showToast(message: string, type: 'error' | 'warning' | 'info' = 'error'): void {
  // Ensure toast container exists
  let container = document.getElementById('omni-toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'omni-toast-container';
    container.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 10000;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 420px;
    `;
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');

  const bgColors: Record<string, string> = {
    error: 'linear-gradient(135deg, #ff4444 0%, #cc0000 100%)',
    warning: 'linear-gradient(135deg, #ff9800 0%, #e65100 100%)',
    info: 'linear-gradient(135deg, #2196F3 0%, #0d47a1 100%)'
  };

  toast.style.cssText = `
    background: ${bgColors[type]};
    color: white;
    padding: 14px 20px;
    border-radius: 12px;
    font-family: 'Inter', 'Segoe UI', sans-serif;
    font-size: 13px;
    line-height: 1.5;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(10px);
    animation: omniToastSlideIn 0.3s ease-out;
    cursor: pointer;
    transition: opacity 0.3s ease, transform 0.3s ease;
  `;

  toast.textContent = message;
  toast.addEventListener('click', () => dismissToast(toast));

  // Inject keyframes if not already present
  if (!document.getElementById('omni-toast-styles')) {
    const style = document.createElement('style');
    style.id = 'omni-toast-styles';
    style.textContent = `
      @keyframes omniToastSlideIn {
        from { opacity: 0; transform: translateX(100px) scale(0.8); }
        to { opacity: 1; transform: translateX(0) scale(1); }
      }
      @keyframes omniToastSlideOut {
        from { opacity: 1; transform: translateX(0) scale(1); }
        to { opacity: 0; transform: translateX(100px) scale(0.8); }
      }
    `;
    document.head.appendChild(style);
  }

  container.appendChild(toast);

  // Auto-dismiss after 6 seconds
  setTimeout(() => dismissToast(toast), 6000);
}

function dismissToast(toast: HTMLDivElement): void {
  toast.style.animation = 'omniToastSlideOut 0.3s ease-in forwards';
  setTimeout(() => toast.remove(), 300);
}
