#ifndef GA_H
#define GA_H

#include "Nodepair.h"
#include "K2.h"
#include "Network.h"
#include <vector>
#include <string>
#include <iostream>
#include <math.h>
#include <fstream>
#include <cstdlib>
#include <ctime>

using namespace std;

class GA: public K2
{
public:

	GA(int numOfNodes, vector<string> nodes, vector< vector<int> > dataset, int maxParents, string fileName);
	vector<Network> makePopulation(int numOfInds, Network& n1, Network& n2);
	void mate(Network n1, Network n2);
	void mutate(vector<int> &genome);
	vector<int> getGenome(int i);
	Network getTopNetwork();	

private:
	
	void dagify(vector<int> &genome);
	bool depthFirstSearch(int start, int search, vector<int>& dag, vector<int>& visited);
	void cap(vector<int>& genome);
	bool elementof(int i, vector<int> v);
	int size;
	vector<Network> networks;
	Network topNetwork;
	Network nextTopNetwork;
	float topScore;
	float nextTopScore;
	vector<string> nodes;
	int numOfNodes;
	
};

#endif