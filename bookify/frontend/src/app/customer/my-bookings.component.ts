import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Booking, BookingStatus, Page } from '../core/models';
import { ToastService } from '../core/toast.service';
import { errMsg } from '../core/util';
import { SlotPickerComponent } from '../shared/slot-picker.component';

type Mode = 'reschedule' | 'review';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [RouterLink, DatePipe, CurrencyPipe, SlotPickerComponent],
  template: `
    <h1>My bookings</h1>
    <div class="tabs" role="tablist" style="margin: 1rem 0 1.5rem">
      @for (t of tabs; track t.label) {
        <button class="tab" role="tab" [class.on]="status() === t.value" [attr.aria-selected]="status() === t.value"
                (click)="setStatus(t.value)">{{ t.label }}</button>
      }
    </div>

    @if (loading() && !result()) {
      <p class="muted">Loading…</p>
    } @else if (result(); as r) {
      @if (!r.content.length) {
        <div class="empty">Nothing here yet. <a routerLink="/providers">Find a provider</a> and book your first appointment.</div>
      } @else {
        <div class="stack">
          @for (b of r.content; track b.id) {
            <article class="panel stack">
              <div class="row between">
                <div>
                  <h3 style="margin: 0"><a [routerLink]="['/providers', b.providerId]">{{ b.providerName }}</a></h3>
                  <div class="small muted">{{ b.serviceName }} · {{ b.price | currency }}</div>
                </div>
                <span [class]="'tag ' + b.status">{{ b.status }}</span>
              </div>
              <div>{{ b.startTime | date: 'EEE d MMM y, h:mm a' }}</div>
              @if (b.notes) { <div class="small muted">Your note: {{ b.notes }}</div> }

              <div class="actions">
                @if (b.status === 'PENDING' || b.status === 'CONFIRMED') {
                  <button class="btn btn-sm" (click)="toggle(b, 'reschedule')">Reschedule</button>
                  <button class="btn btn-sm btn-danger" (click)="cancel(b)">Cancel</button>
                }
                @if (b.status === 'COMPLETED' && !b.reviewed) {
                  <button class="btn btn-sm btn-primary" (click)="toggle(b, 'review')">Leave a review</button>
                }
              </div>

              @if (openId() === b.id && mode() === 'reschedule') {
                <hr class="divider">
                <p class="small muted">Pick a new time. A confirmed booking goes back to "pending" until the provider agrees.</p>
                <app-slot-picker [providerId]="b.providerId" [serviceId]="b.serviceId" (picked)="newStart.set($event)" />
                <button class="btn btn-primary btn-sm" [disabled]="!newStart() || busy()" (click)="reschedule(b)">Confirm new time</button>
              }
              @if (openId() === b.id && mode() === 'review') {
                <hr class="divider">
                <label class="field">Rating
                  <select class="input" style="max-width: 220px" (change)="onRating($event)">
                    <option value="5">5 · Excellent</option>
                    <option value="4">4 · Good</option>
                    <option value="3">3 · Okay</option>
                    <option value="2">2 · Poor</option>
                    <option value="1">1 · Bad</option>
                  </select>
                </label>
                <label class="field">Comment (optional)
                  <textarea class="input" rows="3" maxlength="1000" (input)="onComment($event)"></textarea>
                </label>
                <button class="btn btn-primary btn-sm" [disabled]="busy()" (click)="review(b)">Submit review</button>
              }
            </article>
          }
        </div>
        @if (r.totalPages > 1) {
          <div class="pager">
            <button class="btn btn-sm btn-ghost" [disabled]="r.page === 0" (click)="go(r.page - 1)">Previous</button>
            <span class="small muted">Page {{ r.page + 1 }} of {{ r.totalPages }}</span>
            <button class="btn btn-sm btn-ghost" [disabled]="r.page + 1 >= r.totalPages" (click)="go(r.page + 1)">Next</button>
          </div>
        }
      }
    }
  `
})
export class MyBookingsComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  tabs: { label: string; value: BookingStatus | '' }[] = [
    { label: 'All', value: '' }, { label: 'Pending', value: 'PENDING' }, { label: 'Confirmed', value: 'CONFIRMED' },
    { label: 'Completed', value: 'COMPLETED' }, { label: 'Cancelled', value: 'CANCELLED' }
  ];

  status = signal<BookingStatus | ''>('');
  result = signal<Page<Booking> | null>(null);
  loading = signal(true);
  busy = signal(false);

  openId = signal<number | null>(null);
  mode = signal<Mode | null>(null);
  newStart = signal<string | null>(null);
  rating = signal(5);
  comment = signal('');
  private page = 0;

  constructor() { this.load(); }

  setStatus(s: BookingStatus | '') { this.status.set(s); this.page = 0; this.close(); this.load(); }
  go(p: number) { this.page = p; this.close(); this.load(); }

  toggle(b: Booking, m: Mode) {
    const same = this.openId() === b.id && this.mode() === m;
    this.close();
    if (!same) { this.openId.set(b.id); this.mode.set(m); }
  }
  private close() { this.openId.set(null); this.mode.set(null); this.newStart.set(null); this.rating.set(5); this.comment.set(''); }

  onRating(e: Event) { this.rating.set(Number((e.target as HTMLSelectElement).value)); }
  onComment(e: Event) { this.comment.set((e.target as HTMLTextAreaElement).value); }

  cancel(b: Booking) {
    if (!confirm('Cancel this appointment?')) return;
    this.run(this.api.bookingAction(b.id, 'cancel'), 'Appointment cancelled');
  }

  reschedule(b: Booking) {
    const start = this.newStart();
    if (!start) return;
    this.run(this.api.reschedule(b.id, start), 'Time changed');
  }

  review(b: Booking) {
    this.run(this.api.review({ bookingId: b.id, rating: this.rating(), comment: this.comment().trim() }), 'Thanks for your review');
  }

  private run(call: Observable<unknown>, okText: string) {
    this.busy.set(true);
    call.subscribe({
      next: () => { this.toast.show(okText); this.busy.set(false); this.close(); this.load(); },
      error: e => { this.toast.error(errMsg(e)); this.busy.set(false); }
    });
  }

  private load() {
    this.loading.set(true);
    this.api.myBookings(this.status(), this.page).subscribe({
      next: p => { this.result.set(p); this.loading.set(false); },
      error: e => { this.toast.error(errMsg(e)); this.loading.set(false); }
    });
  }
}
