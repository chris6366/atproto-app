const loading = document.getElementById("loading");
const signedIn = document.getElementById("signed-in");
const loginForm = document.getElementById("login-form");
const did = document.getElementById("did");
const error = document.getElementById("error");
const handleInput = document.getElementById("handle");
const loginButton = document.getElementById("login-button");
const logoutButton = document.getElementById("logout");

async function loadSession() {
  try {
    const res = await fetch("/api/me", {
      credentials: "include",
    });

    if (!res.ok) {
      showLoggedOut();
      return;
    }

    const session = await res.json();

    did.textContent = session.did;
    showLoggedIn();
  } catch {
    showLoggedOut();
  }
}

function showLoggedIn() {
  loading.classList.add("hidden");
  loginForm.classList.add("hidden");
  signedIn.classList.remove("hidden");
}

function showLoggedOut() {
  loading.classList.add("hidden");
  signedIn.classList.add("hidden");
  loginForm.classList.remove("hidden");
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const handle = handleInput.value.trim();
  if (!handle) return;

  loginButton.disabled = true;
  loginButton.textContent = "Signing in...";
  error.classList.add("hidden");
  error.textContent = "";

  try {
    const res = await fetch("/oauth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ handle }),
    });

    const data = await res.json();

    if (!res.ok) {
      throw new Error(data.error || "Login failed");
    }

    window.location.href = data.redirectUrl;
  } catch (err) {
    error.textContent = err instanceof Error ? err.message : "Login failed";
    error.classList.remove("hidden");
    loginButton.disabled = false;
    loginButton.textContent = "Sign in";
  }
});

logoutButton.addEventListener("click", async () => {
  await fetch("/oauth/logout", {
    method: "POST",
    credentials: "include",
  });

  window.location.href = "/";
});

loadSession();
