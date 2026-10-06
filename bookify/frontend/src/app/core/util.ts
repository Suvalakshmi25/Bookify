import { HttpErrorResponse } from '@angular/common/http';

/** Turns an API error into one readable sentence for the UI. */
export function errMsg(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return "Can't reach the server. Check your connection and try again.";
    const body = e.error;
    if (body?.errors && Object.keys(body.errors).length) return Object.values(body.errors).join('. ');
    if (body?.message) return body.message;
  }
  return 'Something went wrong. Please try again.';
}

export function todayStr(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const dayLabel = (d: string) => d.charAt(0) + d.slice(1).toLowerCase();
