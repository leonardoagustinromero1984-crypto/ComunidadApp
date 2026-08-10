export type PublicPet = {
  public_code: string;
  page_kind: "pet_passport";
  display_name: string;
  species?: string | null;
  breed_text?: string | null;
  sex?: string | null;
  birth_date?: string | null;
  primary_color?: string | null;
  distinctive_marks?: string | null;
  microchip_masked?: string | null;
  status: string;
  credentials?: PublicPetCredential[];
  updated_at?: string | null;
  photo_url?: string | null;
};

export type PublicPetCredential = {
  type?: string;
  title?: string;
  issued_at?: string | null;
  expires_at?: string | null;
  status?: string;
  issuer?: string | null;
};

export type PublicAdoption = {
  public_code: string;
  title?: string | null;
  name?: string | null;
  description?: string | null;
  requirements?: string | null;
  species?: string | null;
  sex?: string | null;
  age_years?: number | null;
  age_months?: number | null;
  size?: string | null;
  status: "PUBLISHED" | "ADOPTED" | "CLOSED" | string;
  is_active: boolean;
  location_text?: string | null;
  photo_url?: string | null;
  publisher_display_name?: string | null;
  published_at?: string | null;
  updated_at?: string | null;
};

export type PublicLostFoundCase = {
  public_code: string;
  case_type: "LOST" | "FOUND";
  pet_name?: string | null;
  species?: string | null;
  description?: string | null;
  zone_text?: string | null;
  status: "ACTIVE" | "RESOLVED" | string;
  is_active: boolean;
  photo_url?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
};
