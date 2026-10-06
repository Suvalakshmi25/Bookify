import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errMsg } from '../core/util';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="form-narrow panel stack">
      <h1>Log in</h1>
      @if (error()) { <div class="banner" role="alert">{{ error() }}</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
        <label class="field">Email
          <input class="input" type="email" formControlName="email" autocomplete="email"
                 [class.invalid]="form.controls.email.touched && form.controls.email.invalid">
          @if (form.controls.email.touched && form.controls.email.invalid) { <div class="err">Enter a valid email address</div> }
        </label>
        <label class="field">Password
          <input class="input" type="password" formControlName="password" autocomplete="current-password">
        </label>
        <button class="btn btn-primary" type="submit" [disabled]="loading()">{{ loading() ? 'Logging in…' : 'Log in' }}</button>
      </form>
      <p class="small muted">New here? <a routerLink="/register">Create an account</a></p>
      <hr class="divider">
      <p class="small muted">Demo accounts (password <b>Password&#64;123</b>): customer&#64;bookify.dev, meera&#64;bookify.dev (provider), admin&#64;bookify.dev</p>
    </div>
  `
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });
  loading = signal(false);
  error = signal('');

  submit() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: r => this.router.navigate([this.auth.homeFor(r.user.role)]),
      error: e => { this.error.set(errMsg(e)); this.loading.set(false); }
    });
  }
}
