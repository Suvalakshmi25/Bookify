export type Role = 'ADMIN' | 'PROVIDER' | 'CUSTOMER';
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED';

export interface UserInfo { id: number; fullName: string; email: string; role: Role; }
export interface AuthResponse { accessToken: string; refreshToken: string; user: UserInfo; }

export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

export interface ProviderSummary {
  id: number; name: string; category: string; bio: string | null; ratingAvg: number; ratingCount: number;
}
export interface ServiceDto {
  id: number; name: string; description: string | null; durationMinutes: number; price: number; active: boolean;
}
export interface AvailabilityDto { dayOfWeek: string; startTime: string; endTime: string; }
export interface ProviderDetail extends ProviderSummary { services: ServiceDto[]; availability: AvailabilityDto[]; }
export interface Slot { start: string; end: string; available: boolean; }

export interface Booking {
  id: number; providerId: number; providerName: string; serviceId: number; serviceName: string; price: number;
  customerName: string; startTime: string; endTime: string; status: BookingStatus; notes: string | null; reviewed: boolean;
}
export interface Review { id: number; customerName: string; rating: number; comment: string | null; createdAt: string; }

export interface DayCount { date: string; count: number; }
export interface TopProvider { providerId: number; name: string; bookings: number; }
export interface Analytics {
  totalUsers: number; totalProviders: number; totalCustomers: number; totalBookings: number; revenue: number;
  bookingsPerDay: DayCount[]; topProviders: TopProvider[];
}
export interface AdminUser { id: number; fullName: string; email: string; role: Role; enabled: boolean; createdAt: string; }
