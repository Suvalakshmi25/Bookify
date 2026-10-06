import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <a routerLink="/" class="brand">Bookify</a>
      <nav aria-label="Main">
        <a routerLink="/providers" routerLinkActive="active">Find a provider</a>
        @switch (auth.user()?.role) {
          @case ('CUSTOMER') { <a routerLink="/my-bookings" routerLinkActive="active">My bookings</a> }
          @case ('PROVIDER') {
            <a routerLink="/provider/dashboard" routerLinkActive="active">Appointments</a>
            <a routerLink="/provider/manage" routerLinkActive="active">Services and hours</a>
          }
          @case ('ADMIN') { <a routerLink="/admin" routerLinkActive="active">Admin</a> }
        }
      </nav>
      <div class="who">
        @if (auth.user(); as u) {
          <span class="small muted">{{ u.fullName }}</span>
          <button class="btn btn-ghost btn-sm" (click)="auth.logout()">Log out</button>
        } @else {
          <a routerLink="/login" class="btn btn-ghost btn-sm">Log in</a>
          <a routerLink="/register" class="btn btn-primary btn-sm">Sign up</a>
        }
      </div>
    </header>
    <main class="container"><router-outlet /></main>
    <div class="toasts" aria-live="polite">
      @for (t of toast.items(); track t.id) {
        <div class="toast" [class.error]="t.kind === 'error'">{{ t.text }}</div>
      }
    </div>
  `
})
export class AppComponent {
  auth = inject(AuthService);
  toast = inject(ToastService);
}
