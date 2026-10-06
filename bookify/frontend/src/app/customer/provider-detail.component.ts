import { Component, ViewChild, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ProviderDetail, Review } from '../core/models';
import { ToastService } from '../core/toast.service';
import { DAYS, dayLabel, errMsg } from '../core/util';
import { SlotPickerComponent } from '../shared/slot-picker.component';

/** Provider profile + the booking flow: choose a service, pick a free time, send the request. */
@Component({
  selector: 'app-provider-detail',
  standalone: true,
  imports: [RouterLink, SlotPickerComponent, CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    @if (missing()) {
      <div class="empty">We couldn't find that provider. <a routerLink="/providers">Back to search</a></div>
    } @else if (provider(); as p) {
      <div class="row" style="flex-wrap: nowrap; align-items: flex-start">
        <span class="mono" aria-hidden="true">{{ p.name.charAt(0) }}</span>
        <div>
          <h1 style="margin: 0">{{ p.name }}</h1>
          <div class="muted">{{ p.category }}
            @if (p.ratingCount) { · <span class="rating">★ {{ p.ratingAvg | number: '1.1-1' }}</span> ({{ p.ratingCount }}) }
          </div>
        </div>
      </div>
      <p style="margin-top: 1rem; max-width: 62ch">{{ p.bio }}</p>

      <div class="two-col">
        <section class="panel stack">
          <h2>Book an appointment</h2>
          @if (!p.services.length) {
            <p class="muted">This provider hasn't listed any services yet.</p>
          } @else {
            <div role="radiogroup" aria-label="Choose a service">
              @for (s of p.services; track s.id) {
                <label class="svc" [class.on]="serviceId() === s.id">
                  <input type="radio" name="service" [checked]="serviceId() === s.id" (change)="serviceId.set(s.id)">
                  <span>
                    <b>{{ s.name }}</b><br>
                    <span class="small muted">{{ s.durationMinutes }} min · {{ s.price | currency }}</span>
                    @if (s.description) { <br><span class="small muted">{{ s.description }}</span> }
                  </span>
                </label>
              }
            </div>

            @if (auth.user()?.role === 'CUSTOMER') {
              <app-slot-picker [providerId]="p.id" [serviceId]="serviceId()" (picked)="startTime.set($event)" />
              <label class="field">Note for the provider (optional)
                <textarea class="input" rows="2" maxlength="500" [value]="notes()"
                          (input)="onNotes($event)"></textarea>
              </label>
              <button class="btn btn-primary" [disabled]="!startTime() || busy()" (click)="book()">
                {{ busy() ? 'Sending…' : 'Request this time' }}
              </button>
              <p class="small muted">The provider confirms your request. You'll get an email either way.</p>
            } @else if (!auth.user()) {
              <p class="small muted"><a routerLink="/login">Log in</a> or <a routerLink="/register">sign up</a> as a customer to book.</p>
            } @else {
              <p class="small muted">Only customer accounts can book appointments.</p>
            }
          }
        </section>

        <aside class="stack">
          <section class="panel">
            <h3>Working hours</h3>
            @for (h of hours(); track $index) {
              <div class="row between small">
                <span>{{ label(h.dayOfWeek) }}</span>
                <span>{{ h.startTime.slice(0, 5) }} – {{ h.endTime.slice(0, 5) }}</span>
              </div>
            } @empty {
              <p class="small muted">No hours published yet.</p>
            }
          </section>

          <section class="panel">
            <h3>Reviews</h3>
            @for (r of reviews(); track r.id) {
              <div class="review">
                <div class="row between small">
                  <b>{{ r.customerName }}</b>
                  <span class="muted">{{ r.createdAt | date: 'mediumDate' }}</span>
                </div>
                <div class="rating" aria-label="{{ r.rating }} out of 5">{{ stars(r.rating) }}</div>
                @if (r.comment) { <p class="small" style="margin: .25rem 0 0">{{ r.comment }}</p> }
              </div>
            } @empty {
              <p class="small muted">No reviews yet.</p>
            }
          </section>
        </aside>
      </div>
    } @else {
      <p class="muted">Loading…</p>
    }
  `
})
export class ProviderDetailComponent {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);
  auth = inject(AuthService);

  @ViewChild(SlotPickerComponent) picker?: SlotPickerComponent;

  private id = Number(this.route.snapshot.paramMap.get('id'));
  provider = signal<ProviderDetail | null>(null);
  reviews = signal<Review[]>([]);
  missing = signal(false);

  serviceId = signal<number | null>(null);
  startTime = signal<string | null>(null);
  notes = signal('');
  busy = signal(false);
  label = dayLabel;

  /** The API orders weekdays alphabetically; show them Monday to Sunday. */
  hours = computed(() => [...(this.provider()?.availability ?? [])].sort((a, b) =>
    DAYS.indexOf(a.dayOfWeek) - DAYS.indexOf(b.dayOfWeek) || a.startTime.localeCompare(b.startTime)));

  constructor() {
    this.api.provider(this.id).subscribe({
      next: p => { this.provider.set(p); this.serviceId.set(p.services[0]?.id ?? null); },
      error: () => this.missing.set(true)
    });
    this.api.reviews(this.id).subscribe({ next: r => this.reviews.set(r.content), error: () => {} });
  }

  stars(n: number) { return '★'.repeat(n) + '☆'.repeat(5 - n); }
  onNotes(e: Event) { this.notes.set((e.target as HTMLTextAreaElement).value); }

  book() {
    const startTime = this.startTime();
    const serviceId = this.serviceId();
    if (!startTime || !serviceId) return;
    this.busy.set(true);
    this.api.createBooking({ providerId: this.id, serviceId, startTime, notes: this.notes().trim() || undefined }).subscribe({
      next: () => {
        this.toast.show('Request sent. The provider will confirm shortly.');
        this.router.navigate(['/my-bookings']);
      },
      error: e => {
        this.toast.error(errMsg(e));
        this.busy.set(false);
        this.picker?.load(); // someone may have just taken the slot: refresh the timetable
      }
    });
  }
}
