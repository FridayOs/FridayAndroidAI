export interface FridayUser {
  id: string;
  displayName: string;
  email: string;
  avatarUrl: string;
  plan: string;
}

export interface AuthGoogleBody {
  idToken?: string;
}

export interface FridayJwtPayload {
  sub: string;
  name: string;
  email: string;
  plan: string;
  iat: number;
  exp: number;
}
