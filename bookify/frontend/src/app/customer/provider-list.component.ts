import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Page, ProviderSummary } from '../core/models';
import { ToastService } from '../core/toast.service';
import { errMsg } from '../core/util';

/** Public search page: free text, category, minimum rating, sort, server-side pagination. */
@Component({
  selector: 'app-provider-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  template: `
    <h1>Find a provider</h1>
    <p class="muted">Doctors, coaches, consultants and more. Pick a time that suits you.</p>

    <form [formGroup]="form" class="filters" role="search">
      <label class="field">Search
        <input class="input" type="search" formControlName="q" placeholder="Name, category or topic">
      </label>
      <label class="field">Category
        <select class="input" formControlName="category">
          <option value="">All categories</option>
          @for (c of categories(); track c) { <option [value]="c">{{ c }}</option> }
        </select>
      </label>
      <label class="field">Rating
        <select class="input" formControlName="minRating">
          <option [ngValue]="0">Any rating</option>
          <option [ngValue]="3">3 and up</option>
          <option [ngValue]="4">4 and up</option>
        </select>
      </label>
      <label class="field">Sort by
        <select class="input" formControlName="sort">
          <option value="rating">Highest rated</option>
          <option value="reviews">Most reviewed</option>
          <option value="name">Name (A to Z)</option>
        </select>
      </label>
    </form>

    @if (loading() && !result()) {
      <p class="muted">Loading providers…</p>
    } @else if (result(); as r) {
      @if (!r.content.length) {
        <div class="empty">No providers match those filters. Try clearing one.</div>
      } @else {
        <div class="grid">
          @for (p of r.content; track p.id) {
            <a class="panel link" [routerLink]="['/providers', p.id]">
              <div class="row" style="flex-wrap: nowrap">
                <span class="mono" aria-hidden="true">{{ p.name.charAt(0) }}</span>
                <div>
                  <h3 style="margin: 0">{{ p.name }}</h3>
                  <div class="small muted">{{ p.category }}</div>
                </div>
              </div>
              <p class="clamp" style="margin-top: .75rem">{{ p.bio }}</p>
              <div class="small">
                @if (p.ratingCount) {
                  <span class="rating">★ {{ p.ratingAvg | number: '1.1-1' }}</span>
                  <span class="muted"> ({{ p.ratingCount }} reviews)</span>
                } @else {
                  <span class="muted">New on Bookify</span>
                }
              </div>
            </a>
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
export class ProviderListComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({ q: [''], category: [''], minRating: [0], sort: ['rating'] });
  categories = signal<string[]>([]);
  result = signal<Page<ProviderSummary> | null>(null);
  loading = signal(true);
  private page = 0;

  constructor() {
    this.api.categories().subscribe({ next: c => this.categories.set(c), error: () => {} });
    // wait for the user to pause typing so we don't call the API on every keystroke
    this.form.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(() => { this.page = 0; this.load(); });
    this.load();
  }

  go(p: number) { this.page = p; this.load(); }

  private load() {
    const f = this.form.getRawValue();
    this.loading.set(true);
    this.api.searchProviders({
      q: f.q.trim() || undefined,
      category: f.category || undefined,
      minRating: f.minRating || undefined,
      sort: f.sort,
      dir: f.sort === 'name' ? 'asc' : 'desc',
      page: this.page,
      size: 9
    }).subscribe({
      next: p => { this.result.set(p); this.loading.set(false); },
      error: e => { this.toast.error(errMsg(e)); this.loading.set(false); }
    });
  }
}
