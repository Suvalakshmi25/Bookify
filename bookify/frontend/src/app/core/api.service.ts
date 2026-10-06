import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  Analytics, AdminUser, AvailabilityDto, Booking, BookingStatus, Page, ProviderDetail, ProviderSummary,
  Review, ServiceDto, Slot
} from './models';

export interface SearchParams {
  q?: string; category?: string; minRating?: number; sort: string; dir: string; page: number; size: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // ----- discovery (public) -----
  searchProviders(p: SearchParams) {
    let params = new HttpParams().set('sort', p.sort).set('dir', p.dir).set('page', p.page).set('size', p.size);
    if (p.q) params = params.set('q', p.q);
    if (p.category) params = params.set('category', p.category);
    if (p.minRating) params = params.set('minRating', p.minRating);
    return this.http.get<Page<ProviderSummary>>('/api/providers', { params });
  }
  categories() { return this.http.get<string[]>('/api/providers/categories'); }
  provider(id: number) { return this.http.get<ProviderDetail>(`/api/providers/${id}`); }
  slots(providerId: number, serviceId: number, date: string) {
    const params = new HttpParams().set('serviceId', serviceId).set('date', date);
    return this.http.get<Slot[]>(`/api/providers/${providerId}/slots`, { params });
  }
  reviews(providerId: number) { return this.http.get<Page<Review>>(`/api/providers/${providerId}/reviews`); }

  // ----- bookings -----
  createBooking(body: { providerId: number; serviceId: number; startTime: string; notes?: string }) {
    return this.http.post<Booking>('/api/bookings', body);
  }
  myBookings(status: BookingStatus | '', page: number, size = 8) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<Booking>>('/api/bookings/me', { params });
  }
  bookingAction(id: number, action: 'cancel' | 'confirm' | 'reject' | 'complete') {
    return this.http.patch<Booking>(`/api/bookings/${id}/${action}`, {});
  }
  reschedule(id: number, startTime: string) {
    return this.http.patch<Booking>(`/api/bookings/${id}/reschedule`, { startTime });
  }
  review(body: { bookingId: number; rating: number; comment: string }) {
    return this.http.post<Review>('/api/reviews', body);
  }

  // ----- provider self-management -----
  myServices() { return this.http.get<ServiceDto[]>('/api/provider/services'); }
  saveService(body: { name: string; description: string; durationMinutes: number; price: number }, id?: number) {
    return id
      ? this.http.put<ServiceDto>(`/api/provider/services/${id}`, body)
      : this.http.post<ServiceDto>('/api/provider/services', body);
  }
  deleteService(id: number) { return this.http.delete<void>(`/api/provider/services/${id}`); }
  myAvailability() { return this.http.get<AvailabilityDto[]>('/api/provider/availability'); }
  saveAvailability(rules: AvailabilityDto[]) { return this.http.put<AvailabilityDto[]>('/api/provider/availability', { rules }); }

  // ----- admin -----
  analytics(days = 14) { return this.http.get<Analytics>('/api/admin/analytics/summary', { params: { days } }); }
  adminUsers(page: number) { return this.http.get<Page<AdminUser>>('/api/admin/users', { params: { page, size: 8 } }); }
  setUserEnabled(id: number, value: boolean) {
    return this.http.patch<AdminUser>(`/api/admin/users/${id}/enabled`, {}, { params: { value } });
  }
}
