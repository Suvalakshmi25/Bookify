import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; text: string; kind: 'ok' | 'error'; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly items = signal<Toast[]>([]);
  private next = 1;

  show(text: string, kind: 'ok' | 'error' = 'ok') {
    const id = this.next++;
    this.items.update(list => [...list, { id, text, kind }]);
    setTimeout(() => this.items.update(list => list.filter(t => t.id !== id)), 4500);
  }
  error(text: string) { this.show(text, 'error'); }
}
