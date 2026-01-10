export enum Gender {
  M = 'M',
  F = 'F'
}

export enum MemberStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  DELETED = 'DELETED'
}

export interface Member {
  id: number;
  lastName: string;
  firstName: string;
  discriminator?: string;

  gender?: Gender;
  birthDate?: string;
  phoneNumber?: string;
  email?: string;

  street?: string;
  zipCode?: string;
  city?: string;

  registrationDate?: string;
  memberStatus: MemberStatus;
  role?: string;

  groupName?: string;
}

export interface CreateMemberRequest {
  lastName: string;
  firstName: string;
  gender?: Gender;

  street?: string;
  zipCode?: string;
  city?: string;
}
