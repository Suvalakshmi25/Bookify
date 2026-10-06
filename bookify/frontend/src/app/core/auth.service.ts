import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, finalize, shareReplay, tap } from 'rxjs';
import { AuthResponse, Role } from './models';

const KEY = 'bookify.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private state = signal<AuthResponse | null>(this.load());
  readonly user = computed(() => this.state()?.user ?? null);
  readonly isLoggedIn = computed(() => this.state() !== null);

  /** One refresh at a time: parallel 401s share the same in-flight request (refresh tokens are single-use). */
  private inflight$: Observable<AuthResponse> | null = null;

  get accessToken(): string | null { return this.state()?.accessToken ?? null; }
  get refreshToken(): string | null { return this.state()?.refreshToken ?? null; }

  login(email: string, password: string) {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(tap(r => this.store(r)));
  }

  register(body: { fullName: string; email: string; password: string; role: Role; category?: string; bio?: string }) {
    return this.http.post<AuthResponse>('/api/auth/register', body).pipe(tap(r => this.store(r)));
  }

  refresh(): Observable<AuthResponse> {
    if (!this.inflight$) {
      this.inflight$ = this.http.post<AuthResponse>('/api/auth/refresh', { refreshToken: this.refreshToken }).pipe(
        tap(r => this.store(r)),
        finalize(() => (this.inflight$ = null)),
        shareReplay(1)
      );
    }
    return this.inflight$;
  }

  logout() {
    const rt = this.refreshToken;
    if (rt) this.http.post('/api/auth/logout', { refreshToken: rt }).subscribe({ error: () => {} });
    this.state.set(null);
    localStorage.removeItem(KEY);
    this.router.navigate(['/login']);
  }

  homeFor(role: Role): string {
    switch (role) {
      case 'ADMIN': return '/admin';
      case 'PROVIDER': return '/provider/dashboard';
      default: return '/providers';
    }
  }

  private store(r: AuthResponse) {
    this.state.set(r);
    localStorage.setItem(KEY, JSON.stringify(r));
  }

  private load(): AuthResponse | null {
    try { return JSON.parse(localStorage.getItem(KEY) ?? 'null'); } catch { return null; }
  }
}
