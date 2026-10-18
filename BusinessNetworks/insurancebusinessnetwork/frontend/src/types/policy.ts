
export type Policy = {
  id: string;
  insurer: string;
  insuree: string;
  careProvider: string;
  networkId: string;
  status: string;
};

export type CreatePolicyRequest = {
  networkId: string;
  careProvider: string;
  insuree: string;
};
