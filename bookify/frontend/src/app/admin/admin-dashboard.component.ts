import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ApiService } from '../core/api.service';
import { AdminUser, Analytics, Page } from '../core/models';
import { ToastService } from '../core/toast.service';
import { errMsg } from '../core/util';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe],
  template: `
    <h1>Admin</h1>

    @if (data(); as d) {
      <div class="stats" style="margin: 1.25rem 0">
        <div class="panel stat"><b>{{ d.totalUsers }}</b><span class="small muted">Users</span></div>
        <div class="panel stat"><b>{{ d.totalProviders }}</b><span class="small muted">Providers</span></div>
        <div class="panel stat"><b>{{ d.totalCustomers }}</b><span class="small muted">Customers</span></div>
        <div class="panel stat"><b>{{ d.totalBookings }}</b><span class="small muted">Bookings</span></div>
        <div class="panel stat"><b>{{ d.revenue | currency }}</b><span class="small muted">Revenue (confirmed + completed)</span></div>
      </div>

      <section class="panel">
        <div class="row between">
          <h2 style="margin: 0">Bookings per day</h2>
          <div class="tabs">
            @for (n of ranges; track n) {
              <button class="tab" [class.on]="days() === n" (click)="setDays(n)">{{ n }} days</button>
            }
          </div>
        </div>
        <div class="bars" role="img" aria-label="Bar chart of bookings per day">
          @for (c of d.bookingsPerDay; track c.date) {
            <div class="bar-col">
              <span class="bar-val">{{ c.count }}</span>
              <div class="bar" [style.height.%]="(c.count / max()) * 80"></div>
              <span class="bar-label">{{ c.date | date: 'd/M' }}</span>
            </div>
          }
        </div>
      </section>

      <section class="panel" style="margin-top: 1.25rem">
        <h2>Top providers</h2>
        @for (t of d.topProviders; track t.providerId; let i = $index) {
          <div class="row between" style="padding: .4rem 0; border-bottom: 1px solid var(--line)">
            <span>{{ i + 1 }}. {{ t.name }}</span>
            <b>{{ t.bookings }} bookings</b>
          </div>
        } @empty {
          <p class="muted">No bookings yet.</p>
        }
      </section>
    } @else {
      <p class="muted">Loading…</p>
    }

    <section style="margin-top: 1.5rem">
      <h2>Users</h2>
      @if (users(); as u) {
        <div class="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th><span class="sr-only">Action</span></th></tr></thead>
            <tbody>
              @for (x of u.content; track x.id) {
                <tr>
                  <td>{{ x.fullName }}</td>
                  <td>{{ x.email }}</td>
                  <td>{{ x.role }}</td>
                  <td>{{ x.enabled ? 'Active' : 'Disabled' }}</td>
                  <td>
                    @if (x.role !== 'ADMIN') {
                      <button class="btn btn-sm" [class.btn-danger]="x.enabled" (click)="toggle(x)">
                        {{ x.enabled ? 'Disable' : 'Enable' }}
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (u.totalPages > 1) {
          <div class="pager">
            <button class="btn btn-sm btn-ghost" [disabled]="u.page === 0" (click)="go(u.page - 1)">Previous</button>
            <span class="small muted">Page {{ u.page + 1 }} of {{ u.totalPages }}</span>
            <button class="btn btn-sm btn-ghost" [disabled]="u.page + 1 >= u.totalPages" (click)="go(u.page + 1)">Next</button>
          </div>
        }
      }
    </section>
  `
})
export class AdminDashboardComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  ranges = [7, 14, 30];
  days = signal(14);
  data = signal<Analytics | null>(null);
  users = signal<Page<AdminUser> | null>(null);
  private userPage = 0;

  max = computed(() => Math.max(1, ...(this.data()?.bookingsPerDay.map(d => d.count) ?? [0])));

  constructor() { this.loadAnalytics(); this.loadUsers(); }

  setDays(n: number) { this.days.set(n); this.loadAnalytics(); }
  go(p: number) { this.userPage = p; this.loadUsers(); }

  toggle(u: AdminUser) {
    this.api.setUserEnabled(u.id, !u.enabled).subscribe({
      next: () => { this.toast.show(u.enabled ? 'Account disabled' : 'Account enabled'); this.loadUsers(); },
      error: e => this.toast.error(errMsg(e))
    });
  }

  private loadAnalytics() {
    this.api.analytics(this.days()).subscribe({ next: d => this.data.set(d), error: e => this.toast.error(errMsg(e)) });
  }
  private loadUsers() {
    this.api.adminUsers(this.userPage).subscribe({ next: u => this.users.set(u), error: e => this.toast.error(errMsg(e)) });
  }
}
