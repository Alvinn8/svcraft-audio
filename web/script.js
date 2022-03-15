/**
 * The current page/tab div that the user is seeing.
 */
let currentPage = "loading";

function showPage(page) {
    for (const element of document.getElementsByClassName("page")) {
        element.style.display = "none";
    }
    document.getElementById(page).style.display = "block";
    currentPage = page;
}