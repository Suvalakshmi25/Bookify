import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * 1. Adds the access token to every API call except /api/auth/*.
 * 2. If the server answers 401 (token expired), refreshes once and replays the original request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthCall = req.url.includes('/api/auth/');

  return next(isAuthCall ? req : withToken(req, auth.accessToken)).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isAuthCall && auth.refreshToken) {
        return auth.refresh().pipe(
          switchMap(() => next(withToken(req, auth.accessToken))),
          catchError(refreshErr => { auth.logout(); return throwError(() => refreshErr); })
        );
      }
      return throwError(() => err);
    })
  );
};
