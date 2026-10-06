import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AvailabilityDto, ServiceDto } from '../core/models';
import { ToastService } from '../core/toast.service';
import { DAYS, dayLabel, errMsg } from '../core/util';

/** Providers manage what they sell (services) and when they work (weekly hours). */
@Component({
  selector: 'app-provider-manage',
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe],
  template: `
    <h1>Services and hours</h1>

    <section class="panel stack" style="margin-top: 1.25rem">
      <h2>Your services</h2>
      @for (s of services(); track s.id) {
        <div class="row between" style="padding: .5rem 0; border-bottom: 1px solid var(--line)">
          <div>
            <b>{{ s.name }}</b>
            <div class="small muted">{{ s.durationMinutes }} min · {{ s.price | currency }}@if (s.description) { · {{ s.description }} }</div>
          </div>
          <div class="actions">
            <button class="btn btn-sm" (click)="edit(s)">Edit</button>
            <button class="btn btn-sm btn-danger" (click)="remove(s)">Remove</button>
          </div>
        </div>
      } @empty {
        <p class="muted">No services yet. Add one below so customers can book you.</p>
      }

      <form [formGroup]="form" (ngSubmit)="saveService()" class="stack">
        <h3>{{ editingId() ? 'Edit service' : 'Add a service' }}</h3>
        <label class="field">Name
          <input class="input" formControlName="name" [class.invalid]="form.controls.name.touched && form.controls.name.invalid">
          @if (form.controls.name.touched && form.controls.name.invalid) { <div class="err">Give the service a name</div> }
        </label>
        <label class="field">Description (optional)
          <input class="input" formControlName="description" maxlength="500">
        </label>
        <div class="row">
          <label class="field" style="flex: 1">Duration (minutes)
            <input class="input" type="number" formControlName="durationMinutes">
            @if (form.controls.durationMinutes.invalid) { <div class="err">Between 5 and 480 minutes</div> }
          </label>
          <label class="field" style="flex: 1">Price
            <input class="input" type="number" step="0.01" formControlName="price">
            @if (form.controls.price.invalid) { <div class="err">Enter 0 or more</div> }
          </label>
        </div>
        <div class="row">
          <button class="btn btn-primary" type="submit" [disabled]="busy()">{{ editingId() ? 'Save changes' : 'Add service' }}</button>
          @if (editingId()) { <button class="btn btn-ghost" type="button" (click)="reset()">Cancel</button> }
        </div>
      </form>
    </section>

    <section class="panel stack" style="margin-top: 1.25rem">
      <h2>Weekly working hours</h2>
      <p class="small muted">Customers can book any free slot inside these windows. You can add several windows per day, for example a lunch break.</p>
      @for (r of rules(); track $index; let i = $index) {
        <div class="row">
          <select class="input" style="width: auto; margin: 0" aria-label="Day" (change)="setRule(i, 'dayOfWeek', $event)">
            @for (d of days; track d) { <option [value]="d" [selected]="d === r.dayOfWeek">{{ label(d) }}</option> }
          </select>
          <input class="input" style="width: auto; margin: 0" type="time" aria-label="Opens" [value]="r.startTime" (change)="setRule(i, 'startTime', $event)">
          <span class="muted">to</span>
          <input class="input" style="width: auto; margin: 0" type="time" aria-label="Closes" [value]="r.endTime" (change)="setRule(i, 'endTime', $event)">
          <button class="btn btn-sm btn-ghost" type="button" (click)="removeRule(i)">Remove</button>
        </div>
      } @empty {
        <p class="muted">No hours set, so nobody can book you yet.</p>
      }
      <div class="row">
        <button class="btn btn-ghost" type="button" (click)="addRule()">Add a window</button>
        <button class="btn btn-primary" type="button" [disabled]="busy()" (click)="saveHours()">Save hours</button>
      </div>
    </section>
  `
})
export class ProviderManageComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  days = DAYS;
  label = dayLabel;

  services = signal<ServiceDto[]>([]);
  rules = signal<AvailabilityDto[]>([]);
  editingId = signal<number | null>(null);
  busy = signal(false);

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    durationMinutes: [30, [Validators.required, Validators.min(5), Validators.max(480)]],
    price: [0, [Validators.required, Validators.min(0)]]
  });

  constructor() {
    this.loadServices();
    this.api.myAvailability().subscribe({
      next: list => this.rules.set(
        list.map(r => ({ ...r, startTime: r.startTime.slice(0, 5), endTime: r.endTime.slice(0, 5) }))
            .sort((a, b) => DAYS.indexOf(a.dayOfWeek) - DAYS.indexOf(b.dayOfWeek) || a.startTime.localeCompare(b.startTime))),
      error: e => this.toast.error(errMsg(e))
    });
  }

  // ---------- services ----------
  edit(s: ServiceDto) {
    this.editingId.set(s.id);
    this.form.setValue({ name: s.name, description: s.description ?? '', durationMinutes: s.durationMinutes, price: s.price });
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
  }

  reset() {
    this.editingId.set(null);
    this.form.reset({ name: '', description: '', durationMinutes: 30, price: 0 });
  }

  saveService() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.busy.set(true);
    this.api.saveService(this.form.getRawValue(), this.editingId() ?? undefined).subscribe({
      next: () => { this.toast.show('Service saved'); this.busy.set(false); this.reset(); this.loadServices(); },
      error: e => { this.toast.error(errMsg(e)); this.busy.set(false); }
    });
  }

  remove(s: ServiceDto) {
    if (!confirm(`Remove "${s.name}"? Existing bookings are kept.`)) return;
    this.api.deleteService(s.id).subscribe({
      next: () => { this.toast.show('Service removed'); this.loadServices(); },
      error: e => this.toast.error(errMsg(e))
    });
  }

  private loadServices() {
    this.api.myServices().subscribe({ next: s => this.services.set(s), error: e => this.toast.error(errMsg(e)) });
  }

  // ---------- hours ----------
  addRule() { this.rules.update(list => [...list, { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '17:00' }]); }
  removeRule(i: number) { this.rules.update(list => list.filter((_, idx) => idx !== i)); }

  setRule(i: number, key: keyof AvailabilityDto, e: Event) {
    const value = (e.target as HTMLInputElement | HTMLSelectElement).value;
    this.rules.update(list => list.map((r, idx) => (idx === i ? { ...r, [key]: value } : r)));
  }

  saveHours() {
    this.busy.set(true);
    this.api.saveAvailability(this.rules()).subscribe({
      next: () => { this.toast.show('Working hours saved'); this.busy.set(false); },
      error: e => { this.toast.error(errMsg(e)); this.busy.set(false); }
    });
  }
}
