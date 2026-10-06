import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../core/api.service';
import { Booking, BookingStatus, Page } from '../core/models';
import { ToastService } from '../core/toast.service';
import { errMsg } from '../core/util';

@Component({
  selector: 'app-provider-dashboard',
  standalone: true,
  imports: [DatePipe],
  template: `
    <h1>Appointments</h1>
    <p class="muted">Confirm or reject new requests, and mark finished sessions as complete so customers can review you.</p>

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
        <div class="empty">No appointments in this view.</div>
      } @else {
        <div class="table-wrap">
          <table>
            <thead><tr><th>When</th><th>Customer</th><th>Service</th><th>Status</th><th><span class="sr-only">Actions</span></th></tr></thead>
            <tbody>
              @for (b of r.content; track b.id) {
                <tr>
                  <td>{{ b.startTime | date: 'EEE d MMM, h:mm a' }}</td>
                  <td>{{ b.customerName }}@if (b.notes) { <div class="small muted">“{{ b.notes }}”</div> }</td>
                  <td>{{ b.serviceName }}</td>
                  <td><span [class]="'tag ' + b.status">{{ b.status }}</span></td>
                  <td>
                    <div class="actions">
                      @if (b.status === 'PENDING') {
                        <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="act(b, 'confirm', 'Booking confirmed')">Confirm</button>
                        <button class="btn btn-sm btn-danger" [disabled]="busy()" (click)="reject(b)">Reject</button>
                      }
                      @if (b.status === 'CONFIRMED') {
                        <button class="btn btn-sm" [disabled]="busy()" (click)="act(b, 'complete', 'Marked as completed')">Mark complete</button>
                        <button class="btn btn-sm btn-danger" [disabled]="busy()" (click)="cancel(b)">Cancel</button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
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
export class ProviderDashboardComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  tabs: { label: string; value: BookingStatus | '' }[] = [
    { label: 'Needs a reply', value: 'PENDING' }, { label: 'Upcoming', value: 'CONFIRMED' },
    { label: 'Completed', value: 'COMPLETED' }, { label: 'All', value: '' }
  ];

  status = signal<BookingStatus | ''>('PENDING');
  result = signal<Page<Booking> | null>(null);
  loading = signal(true);
  busy = signal(false);
  private page = 0;

  constructor() { this.load(); }

  setStatus(s: BookingStatus | '') { this.status.set(s); this.page = 0; this.load(); }
  go(p: number) { this.page = p; this.load(); }

  reject(b: Booking) { if (confirm(`Reject the request from ${b.customerName}?`)) this.act(b, 'reject', 'Request rejected'); }
  cancel(b: Booking) { if (confirm(`Cancel the appointment with ${b.customerName}?`)) this.act(b, 'cancel', 'Appointment cancelled'); }

  act(b: Booking, action: 'confirm' | 'reject' | 'complete' | 'cancel', okText: string) {
    this.busy.set(true);
    this.api.bookingAction(b.id, action).subscribe({
      next: () => { this.toast.show(okText); this.busy.set(false); this.load(); },
      error: e => { this.toast.error(errMsg(e)); this.busy.set(false); this.load(); }
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
