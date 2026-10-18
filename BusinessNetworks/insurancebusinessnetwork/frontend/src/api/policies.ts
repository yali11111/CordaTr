
import type { CreatePolicyRequest, Policy } from "../types/policy";

const API_BASE_URL = "/api";

async function readError(response: Response): Promise<string> {
  const body = await response.text();

  return body || `Erreur HTTP ${response.status}`;
}

export async function getPolicies(): Promise<Policy[]> {
  const response = await fetch(`${API_BASE_URL}/policies`);

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<Policy[]>;
}

export async function getPolicy(id: string): Promise<Policy> {
  const response = await fetch(
    `${API_BASE_URL}/policies/${encodeURIComponent(id)}`
  );

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<Policy>;
}

export async function createPolicy(
  request: CreatePolicyRequest
): Promise<Policy> {
  const response = await fetch(`${API_BASE_URL}/policies`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<Policy>;
}
