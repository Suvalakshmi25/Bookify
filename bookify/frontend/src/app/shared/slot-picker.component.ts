import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../core/api.service';
import { Slot } from '../core/models';
import { todayStr } from '../core/util';

/** Pick a day, see that provider's timetable for the chosen service, click a free time. */
@Component({
  selector: 'app-slot-picker',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <label class="field">Day
      <input class="input" type="date" [formControl]="date" [min]="today" style="max-width: 200px">
    </label>
    @if (loading()) {
      <p class="muted small" style="margin-top:.75rem">Loading times…</p>
    } @else if (!slots().length) {
      <p class="muted small" style="margin-top:.75rem">No working hours on this day. Try another date.</p>
    } @else {
      <div class="timetable" role="listbox" aria-label="Available times">
        @for (s of slots(); track s.start) {
          <button type="button" class="slot" role="option" [disabled]="!s.available"
                  [class.selected]="selected() === s.start" [attr.aria-selected]="selected() === s.start"
                  (click)="choose(s)">{{ s.start | date: 'shortTime' }}</button>
        }
      </div>
    }
  `
})
export class SlotPickerComponent implements OnChanges {
  @Input({ required: true }) providerId!: number;
  @Input() serviceId: number | null = null;
  @Output() picked = new EventEmitter<string | null>();

  private api = inject(ApiService);
  today = todayStr();
  date = new FormControl(todayStr(1), { nonNullable: true });
  slots = signal<Slot[]>([]);
  loading = signal(false);
  selected = signal<string | null>(null);

  constructor() {
    this.date.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.load());
  }

  ngOnChanges() { this.load(); }

  load() {
    this.selected.set(null);
    this.picked.emit(null);
    if (!this.serviceId || !this.date.value) { this.slots.set([]); return; }
    this.loading.set(true);
    this.api.slots(this.providerId, this.serviceId, this.date.value).subscribe({
      next: s => { this.slots.set(s); this.loading.set(false); },
      error: () => { this.slots.set([]); this.loading.set(false); }
    });
  }

  choose(s: Slot) {
    if (!s.available) return;
    this.selected.set(s.start);
    this.picked.emit(s.start);
  }
}
