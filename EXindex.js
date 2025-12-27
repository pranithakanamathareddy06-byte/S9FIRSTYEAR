function bill() {
let rice = document.getElementById("r").value * 60;
let sugar = document.getElementById("s").value * 45;
let milk = document.getElementById("m").value * 55;
    let total = rice + sugar + milk;

    document.getElementById("result").innerHTML =
        "Total Bill =  " + total;
}