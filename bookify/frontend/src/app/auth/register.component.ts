import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errMsg } from '../core/util';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="form-narrow panel stack">
      <h1>Create your account</h1>
      @if (error()) { <div class="banner" role="alert">{{ error() }}</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
        <div class="tabs" role="radiogroup" aria-label="Account type">
          <button type="button" class="tab" [class.on]="form.controls.role.value === 'CUSTOMER'" (click)="setRole('CUSTOMER')">I want to book</button>
          <button type="button" class="tab" [class.on]="form.controls.role.value === 'PROVIDER'" (click)="setRole('PROVIDER')">I offer appointments</button>
        </div>
        <label class="field">Full name
          <input class="input" formControlName="fullName" autocomplete="name"
                 [class.invalid]="form.controls.fullName.touched && form.controls.fullName.invalid">
        </label>
        <label class="field">Email
          <input class="input" type="email" formControlName="email" autocomplete="email"
                 [class.invalid]="form.controls.email.touched && form.controls.email.invalid">
          @if (form.controls.email.touched && form.controls.email.invalid) { <div class="err">Enter a valid email address</div> }
        </label>
        <label class="field">Password
          <input class="input" type="password" formControlName="password" autocomplete="new-password"
                 [class.invalid]="form.controls.password.touched && form.controls.password.invalid">
          @if (form.controls.password.touched && form.controls.password.invalid) { <div class="err">Use at least 8 characters</div> }
        </label>
        @if (form.controls.role.value === 'PROVIDER') {
          <label class="field">What do you do?
            <input class="input" formControlName="category" placeholder="Dentist, personal trainer, tutor…">
          </label>
          <label class="field">About you
            <textarea class="input" rows="3" formControlName="bio" placeholder="A few lines customers will see on your profile"></textarea>
          </label>
        }
        <button class="btn btn-primary" type="submit" [disabled]="loading()">{{ loading() ? 'Creating account…' : 'Create account' }}</button>
      </form>
      <p class="small muted">Already registered? <a routerLink="/login">Log in</a></p>
    </div>
  `
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    role: ['CUSTOMER' as 'CUSTOMER' | 'PROVIDER'],
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    category: [''],
    bio: ['']
  });
  loading = signal(false);
  error = signal('');

  setRole(role: 'CUSTOMER' | 'PROVIDER') { this.form.controls.role.setValue(role); }

  submit() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    this.auth.register(v.role === 'PROVIDER' ? v : { fullName: v.fullName, email: v.email, password: v.password, role: v.role }).subscribe({
      next: r => this.router.navigate([this.auth.homeFor(r.user.role)]),
      error: e => { this.error.set(errMsg(e)); this.loading.set(false); }
    });
  }
}
