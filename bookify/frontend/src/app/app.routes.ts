import { Routes } from '@angular/router';
import { roleGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'providers' },
  { path: 'login', loadComponent: () => import('./auth/login.component').then(m => m.LoginComponent) },
  { path: 'register', loadComponent: () => import('./auth/register.component').then(m => m.RegisterComponent) },

  // public
  { path: 'providers', loadComponent: () => import('./customer/provider-list.component').then(m => m.ProviderListComponent) },
  { path: 'providers/:id', loadComponent: () => import('./customer/provider-detail.component').then(m => m.ProviderDetailComponent) },

  // customer
  { path: 'my-bookings', canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () => import('./customer/my-bookings.component').then(m => m.MyBookingsComponent) },

  // provider
  { path: 'provider/dashboard', canActivate: [roleGuard('PROVIDER')],
    loadComponent: () => import('./provider/provider-dashboard.component').then(m => m.ProviderDashboardComponent) },
  { path: 'provider/manage', canActivate: [roleGuard('PROVIDER')],
    loadComponent: () => import('./provider/provider-manage.component').then(m => m.ProviderManageComponent) },

  // admin
  { path: 'admin', canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./admin/admin-dashboard.component').then(m => m.AdminDashboardComponent) },

  { path: '**', redirectTo: 'providers' }
];
